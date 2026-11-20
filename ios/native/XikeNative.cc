#include "XikeNative.h"

#include <archive.h>
#include <archive_entry.h>
#include <fcntl.h>
#include <openssl/aead.h>
#include <openssl/crypto.h>
#include <openssl/digest.h>
#include <openssl/evp.h>
#include <openssl/rand.h>
#include <unistd.h>

#include <algorithm>
#include <array>
#include <climits>
#include <cstring>
#include <memory>
#include <string>
#include <vector>

#include "tink/subtle/aes_gcm_hkdf_streaming.h"
#include "tink/util/buffer.h"
#include "tink/util/file_input_stream.h"
#include "tink/util/file_output_stream.h"
#include "tink/util/file_random_access_stream.h"
#include "tink/util/input_stream_util.h"
#include "tink/util/secret_data.h"
#include "mkvparser/mkvparser.h"

using crypto::tink::InputStream;
using crypto::tink::OutputStream;
using crypto::tink::RandomAccessStream;
using crypto::tink::subtle::AesGcmHkdfStreaming;

namespace {

constexpr std::array<uint8_t, 7> kStreamingHeader{'L', 'I', 'U', 'B', 'A', 'I', 5};
constexpr size_t kSaltSize = 16;
constexpr size_t kNonceSize = 12;
constexpr size_t kTagSize = 16;
constexpr size_t kKeySize = 32;
constexpr size_t kSegmentSize = 64 * 1024;
constexpr size_t kLegacyLimit = 320 * 1024 * 1024;
constexpr int kPasswordIterations = 210000;

using KeyBytes = std::array<uint8_t, kKeySize>;

bool DeriveKey(const uint8_t* password, size_t size, const uint8_t* salt, KeyBytes& key) {
    return PKCS5_PBKDF2_HMAC(reinterpret_cast<const char*>(password), size,
                             salt, kSaltSize, kPasswordIterations, EVP_sha256(),
                             key.size(), key.data()) == 1;
}

auto StreamingCipher(const uint8_t* key) {
    return AesGcmHkdfStreaming::New({
        crypto::tink::util::SecretDataFromStringView(
            absl::string_view(reinterpret_cast<const char*>(key), kKeySize)),
        crypto::tink::subtle::SHA256, kKeySize, kSegmentSize, 0});
}

bool WriteBytes(OutputStream& stream, const void* bytes, size_t size) {
    auto* cursor = static_cast<const uint8_t*>(bytes);
    while (size > 0) {
        void* buffer = nullptr;
        auto available = stream.Next(&buffer);
        if (!available.ok() || *available <= 0) return false;
        size_t count = std::min(size, static_cast<size_t>(*available));
        std::memcpy(buffer, cursor, count);
        stream.BackUp(*available - static_cast<int>(count));
        cursor += count;
        size -= count;
    }
    return true;
}

std::unique_ptr<OutputStream> NewFileWriter(const char* path) {
    int fd = open(path, O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0600);
    return fd == -1 ? nullptr : std::make_unique<crypto::tink::util::FileOutputStream>(fd);
}

bool IsZip(absl::string_view prefix) {
    const auto signature = prefix.substr(0, 4);
    return signature == "PK\x03\x04" || signature == "PK\x05\x06";
}

bool IsEnvelope(absl::string_view prefix) {
    return prefix.size() == kStreamingHeader.size() && prefix.substr(0, 6) == "LIUBAI"
        && (prefix.back() == 4 || prefix.back() == 5);
}

int ReadFailure(bool encrypted) { return encrypted ? XIKE_AUTH : XIKE_FORMAT; }

}  // namespace

struct XikeArchiveReader {
    struct archive* archive = archive_read_new();
    std::unique_ptr<InputStream> stream;
    std::vector<uint8_t> legacy;
    bool encrypted = false;

    ~XikeArchiveReader() {
        if (archive) archive_read_free(archive);
        if (!legacy.empty()) OPENSSL_cleanse(legacy.data(), legacy.size());
    }
};

struct XikeArchiveWriter {
    struct archive* archive = archive_write_new();
    std::unique_ptr<OutputStream> stream;
    std::string path;

    ~XikeArchiveWriter() {
        if (archive) archive_write_free(archive);
    }
};

struct XikeMediaReader {
    std::unique_ptr<RandomAccessStream> stream;
    int64_t length;
};

struct XikeMediaWriter {
    std::unique_ptr<OutputStream> stream;
    std::string path;
};

namespace {

la_ssize_t ReadArchive(struct archive* archive, void* context, const void** buffer) {
    auto& reader = *static_cast<XikeArchiveReader*>(context);
    auto count = reader.stream->Next(buffer);
    if (count.ok()) return *count;
    if (absl::IsOutOfRange(count.status())) return 0;
    archive_set_error(archive, EIO, "Unable to read archive");
    return -1;
}

la_ssize_t WriteArchive(struct archive*, void* context, const void* bytes, size_t count) {
    auto& writer = *static_cast<XikeArchiveWriter*>(context);
    return WriteBytes(*writer.stream, bytes, count) ? static_cast<la_ssize_t>(count) : -1;
}

int OpenLegacyArchive(XikeArchiveReader& reader, const uint8_t* password,
                      size_t password_size, const uint8_t* salt) {
    auto nonce = crypto::tink::ReadBytesFromStream(kNonceSize, reader.stream.get());
    if (!nonce.ok()) return XIKE_FORMAT;
    std::vector<uint8_t> ciphertext;
    for (;;) {
        const void* bytes = nullptr;
        auto length = reader.stream->Next(&bytes);
        if (!length.ok()) {
            if (absl::IsOutOfRange(length.status())) break;
            return XIKE_IO;
        }
        if (static_cast<size_t>(*length) > kLegacyLimit - ciphertext.size()) return XIKE_LIMIT;
        const auto* start = static_cast<const uint8_t*>(bytes);
        ciphertext.insert(ciphertext.end(), start, start + *length);
    }
    if (ciphertext.size() < kTagSize) return XIKE_AUTH;
    reader.legacy.resize(ciphertext.size() - kTagSize);
    size_t size = reader.legacy.size();
    const int result = xike_legacy_decrypt(password, password_size, salt,
        reinterpret_cast<const uint8_t*>(nonce->data()), ciphertext.data(), ciphertext.size(),
        reader.legacy.data(), &size);
    if (result != XIKE_OK) return result;
    return archive_read_open_memory(reader.archive, reader.legacy.data(), size) == ARCHIVE_OK
        ? XIKE_OK : XIKE_FORMAT;
}

}  // namespace

int xike_backup_kind(const char* path) {
    const int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd == -1) return XIKE_IO;
    crypto::tink::util::FileInputStream file(fd);
    auto prefix = crypto::tink::ReadBytesFromStream(kStreamingHeader.size(), &file);
    if (!prefix.ok()) return XIKE_FORMAT;
    if (IsZip(*prefix)) return XIKE_ZIP;
    if (IsEnvelope(*prefix)) return XIKE_ENCRYPTED_ZIP;
    if (prefix->find_first_not_of(" \r\n\t") != std::string::npos
        && (*prefix)[prefix->find_first_not_of(" \r\n\t")] == '{') return XIKE_LEGACY_JSON;
    return XIKE_FORMAT;
}

XikeArchiveReader* xike_archive_reader(const char* path, const uint8_t* password,
                                       size_t password_size, int* result) {
    const int kind = xike_backup_kind(path);
    *result = kind < 0 ? kind : XIKE_FORMAT;
    if (kind != XIKE_ZIP && kind != XIKE_ENCRYPTED_ZIP) return nullptr;
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd == -1) {
        *result = XIKE_IO;
        return nullptr;
    }
    auto reader = std::make_unique<XikeArchiveReader>();
    reader->stream = std::make_unique<crypto::tink::util::FileInputStream>(fd, kSegmentSize);
    if (!reader->archive || archive_read_support_format_zip_streamable(reader->archive) != ARCHIVE_OK)
        return nullptr;
    if (kind == XIKE_ENCRYPTED_ZIP) {
        reader->encrypted = true;
        *result = XIKE_AUTH;
        if (!password || password_size == 0) return nullptr;
        auto header = crypto::tink::ReadBytesFromStream(kStreamingHeader.size(), reader->stream.get());
        auto salt = crypto::tink::ReadBytesFromStream(kSaltSize, reader->stream.get());
        if (!header.ok() || !salt.ok() || !IsEnvelope(*header)) return nullptr;
        auto* salt_bytes = reinterpret_cast<const uint8_t*>(salt->data());
        if (header->back() == 4) {
            *result = OpenLegacyArchive(*reader, password, password_size, salt_bytes);
            return *result == XIKE_OK ? reader.release() : nullptr;
        }
        KeyBytes key{};
        if (!DeriveKey(password, password_size, salt_bytes, key)) return nullptr;
        auto cipher = StreamingCipher(key.data());
        OPENSSL_cleanse(key.data(), key.size());
        if (!cipher.ok()) return nullptr;
        auto stream = (*cipher)->NewDecryptingStream(std::move(reader->stream), *header + *salt);
        if (!stream.ok()) return nullptr;
        reader->stream = *std::move(stream);
    }
    if (archive_read_open(reader->archive, reader.get(), nullptr, ReadArchive, nullptr) != ARCHIVE_OK)
        return nullptr;
    *result = XIKE_OK;
    return reader.release();
}

int xike_archive_next(XikeArchiveReader* reader, const char** name) {
    struct archive_entry* entry = nullptr;
    const int status = archive_read_next_header(reader->archive, &entry);
    if (status == ARCHIVE_EOF) return XIKE_END;
    if (status != ARCHIVE_OK) return ReadFailure(reader->encrypted);
    if (archive_entry_filetype(entry) != AE_IFREG || archive_entry_hardlink(entry)) return XIKE_FORMAT;
    *name = archive_entry_pathname_utf8(entry);
    return *name ? XIKE_OK : XIKE_FORMAT;
}

int64_t xike_archive_read(XikeArchiveReader* reader, uint8_t* data, size_t size) {
    auto count = archive_read_data(reader->archive, data, size);
    return count >= 0 ? count : ReadFailure(reader->encrypted);
}

int xike_archive_verify_end(XikeArchiveReader* reader) {
    // libarchive stops at the ZIP directory. Authenticate the remainder of the Tink stream.
    if (!reader->encrypted || !reader->legacy.empty()) return XIKE_OK;
    for (;;) {
        const void* data = nullptr;
        auto count = reader->stream->Next(&data);
        if (!count.ok()) return absl::IsOutOfRange(count.status()) ? XIKE_OK : XIKE_AUTH;
    }
}

void xike_archive_reader_close(XikeArchiveReader* reader) { delete reader; }

XikeArchiveWriter* xike_archive_writer(const char* path, const uint8_t* password,
                                       size_t password_size, int encrypted, int* result) {
    auto writer = std::make_unique<XikeArchiveWriter>();
    writer->stream = NewFileWriter(path);
    *result = XIKE_IO;
    if (!writer->stream) return nullptr;
    writer->path = path;
    if (encrypted) {
        std::array<uint8_t, kSaltSize> salt{};
        KeyBytes key{};
        if (RAND_bytes(salt.data(), salt.size()) != 1 ||
            !DeriveKey(password, password_size, salt.data(), key)) {
            unlink(path);
            return nullptr;
        }
        auto cipher = StreamingCipher(key.data());
        OPENSSL_cleanse(key.data(), key.size());
        std::string aad(kStreamingHeader.begin(), kStreamingHeader.end());
        aad.append(salt.begin(), salt.end());
        if (!cipher.ok() || !WriteBytes(*writer->stream, aad.data(), aad.size())) {
            unlink(path);
            return nullptr;
        }
        auto stream = (*cipher)->NewEncryptingStream(std::move(writer->stream), aad);
        if (!stream.ok()) {
            unlink(path);
            return nullptr;
        }
        writer->stream = *std::move(stream);
    }
    if (!writer->archive || archive_write_set_format_zip(writer->archive) != ARCHIVE_OK ||
        archive_write_set_bytes_per_block(writer->archive, 0) != ARCHIVE_OK ||
        archive_write_set_options(writer->archive, "zip:compression-level=1") != ARCHIVE_OK ||
        archive_write_open(writer->archive, writer.get(), nullptr, WriteArchive, nullptr) != ARCHIVE_OK) {
        unlink(path);
        return nullptr;
    }
    *result = XIKE_OK;
    return writer.release();
}

int xike_archive_add(XikeArchiveWriter* writer, const char* name, int64_t size) {
    auto* entry = archive_entry_new();
    if (!entry) return XIKE_IO;
    archive_entry_set_pathname_utf8(entry, name);
    archive_entry_set_filetype(entry, AE_IFREG);
    archive_entry_set_perm(entry, 0600);
    archive_entry_set_size(entry, size);
    const int status = archive_write_header(writer->archive, entry);
    archive_entry_free(entry);
    return status == ARCHIVE_OK ? XIKE_OK : XIKE_IO;
}

int xike_archive_write(XikeArchiveWriter* writer, const uint8_t* data, size_t size) {
    return archive_write_data(writer->archive, data, size) == static_cast<la_ssize_t>(size)
        ? XIKE_OK : XIKE_IO;
}

int xike_archive_writer_close(XikeArchiveWriter* writer, int commit) {
    std::unique_ptr<XikeArchiveWriter> owner(writer);
    if (commit && archive_write_close(writer->archive) == ARCHIVE_OK
        && writer->stream->Close().ok()) return XIKE_OK;
    if (writer->archive) archive_write_fail(writer->archive);
    unlink(writer->path.c_str());
    return XIKE_IO;
}

int xike_legacy_decrypt(const uint8_t* password, size_t password_size, const uint8_t* salt,
                        const uint8_t* nonce, const uint8_t* ciphertext, size_t ciphertext_size,
                        uint8_t* plaintext, size_t* plaintext_size) {
    KeyBytes key{};
    if (!password || password_size == 0 || !DeriveKey(password, password_size, salt, key))
        return XIKE_AUTH;
    bssl::ScopedEVP_AEAD_CTX context;
    const bool ready = EVP_AEAD_CTX_init(context.get(), EVP_aead_aes_256_gcm(),
                                       key.data(), key.size(), kTagSize, nullptr) == 1;
    OPENSSL_cleanse(key.data(), key.size());
    if (!ready) return XIKE_AUTH;
    return EVP_AEAD_CTX_open(context.get(), plaintext, plaintext_size, *plaintext_size,
                            nonce, kNonceSize, ciphertext, ciphertext_size, nullptr, 0) == 1
        ? XIKE_OK : XIKE_AUTH;
}

XikeMediaReader* xike_media_reader(const char* path, const uint8_t* key, const char* aad, int* result) {
    *result = XIKE_IO;
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd == -1) return nullptr;
    auto file = std::make_unique<crypto::tink::util::FileRandomAccessStream>(fd);
    auto cipher = StreamingCipher(key);
    *result = XIKE_AUTH;
    if (!cipher.ok()) return nullptr;
    auto stream = (*cipher)->NewDecryptingRandomAccessStream(std::move(file), aad);
    if (!stream.ok()) return nullptr;
    auto length = (*stream)->size();
    if (!length.ok() || *length <= 0) return nullptr;
    *result = XIKE_OK;
    return new XikeMediaReader{*std::move(stream), *length};
}

int64_t xike_media_size(XikeMediaReader* reader) { return reader->length; }

int64_t xike_media_read(XikeMediaReader* reader, int64_t offset, uint8_t* data, size_t size) {
    if (offset < 0 || offset > reader->length || size > INT_MAX) return XIKE_FORMAT;
    const int count = static_cast<int>(std::min<int64_t>(reader->length - offset, size));
    if (count == 0) return 0;
    auto buffer = crypto::tink::util::Buffer::NewNonOwning(reinterpret_cast<char*>(data), count);
    if (!buffer.ok()) return XIKE_IO;
    const auto status = reader->stream->PRead(offset, count, buffer->get());
    // Tink can report end-of-stream together with a complete authenticated read.
    const bool complete_end = absl::IsOutOfRange(status) && offset + count == reader->length;
    return (status.ok() || complete_end) && (*buffer)->size() == count ? count : XIKE_AUTH;
}

void xike_media_reader_close(XikeMediaReader* reader) { delete reader; }

namespace {

class ArchiveVideoReader final : public mkvparser::IMkvReader {
    XikeMediaReader* reader_;

public:
    explicit ArchiveVideoReader(XikeMediaReader* reader) : reader_(reader) {}

    int Read(long long position, long count, unsigned char* data) override {
        if (position < 0 || count < 0 || position > reader_->length ||
            count > reader_->length - position) return -1;
        return xike_media_read(reader_, position, data, count) == count ? 0 : -1;
    }

    int Length(long long* total, long long* available) override {
        if (total) *total = reader_->length;
        if (available) *available = reader_->length;
        return 0;
    }
};

}  // namespace

int64_t xike_matroska_duration(XikeMediaReader* reader) {
    ArchiveVideoReader stream(reader);
    mkvparser::EBMLHeader header;
    long long offset = 0;
    if (header.Parse(&stream, offset) < 0 || !header.m_docType ||
        (std::strcmp(header.m_docType, "matroska") != 0 && std::strcmp(header.m_docType, "webm") != 0))
        return XIKE_FORMAT;
    mkvparser::Segment* raw = nullptr;
    if (mkvparser::Segment::CreateInstance(&stream, offset, raw) != 0 || !raw) return XIKE_FORMAT;
    std::unique_ptr<mkvparser::Segment> segment(raw);
    if (segment->Load() != 0) return XIKE_FORMAT;
    const auto* tracks = segment->GetTracks();
    if (!tracks) return XIKE_FORMAT;
    bool has_video = false;
    for (unsigned long index = 0; index < tracks->GetTracksCount(); ++index) {
        const auto* track = tracks->GetTrackByIndex(index);
        if (track && track->GetType() == mkvparser::Track::kVideo) { has_video = true; break; }
    }
    const int64_t duration = segment->GetDuration() / 1000000;
    return has_video && duration > 0 && duration <= 300000 ? duration : XIKE_FORMAT;
}

XikeMediaWriter* xike_media_writer(const char* path, const uint8_t* key, const char* aad, int* result) {
    *result = XIKE_IO;
    auto file = NewFileWriter(path);
    if (!file) return nullptr;
    auto cipher = StreamingCipher(key);
    if (!cipher.ok()) {
        unlink(path);
        return nullptr;
    }
    auto stream = (*cipher)->NewEncryptingStream(std::move(file), aad);
    if (!stream.ok()) {
        unlink(path);
        return nullptr;
    }
    *result = XIKE_OK;
    return new XikeMediaWriter{*std::move(stream), path};
}

int xike_media_write(XikeMediaWriter* writer, const uint8_t* data, size_t size) {
    return WriteBytes(*writer->stream, data, size) ? XIKE_OK : XIKE_IO;
}

int xike_media_writer_close(XikeMediaWriter* writer, int commit) {
    std::unique_ptr<XikeMediaWriter> owner(writer);
    if (commit && writer->stream->Close().ok()) return XIKE_OK;
    unlink(writer->path.c_str());
    return XIKE_IO;
}
