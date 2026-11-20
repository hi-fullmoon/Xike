#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

enum {
    XIKE_OK = 0,
    XIKE_END = 1,
    XIKE_IO = -1,
    XIKE_FORMAT = -2,
    XIKE_AUTH = -3,
    XIKE_LIMIT = -4,
};

enum {
    XIKE_ZIP = 1,
    XIKE_ENCRYPTED_ZIP = 2,
    XIKE_LEGACY_JSON = 3,
};

typedef struct XikeArchiveReader XikeArchiveReader;
typedef struct XikeArchiveWriter XikeArchiveWriter;
typedef struct XikeMediaReader XikeMediaReader;
typedef struct XikeMediaWriter XikeMediaWriter;

int xike_backup_kind(const char *path);
XikeArchiveReader *xike_archive_reader(const char *path, const uint8_t *password,
                                       size_t password_size, int *result);
int xike_archive_next(XikeArchiveReader *reader, const char **name);
int64_t xike_archive_read(XikeArchiveReader *reader, uint8_t *data, size_t size);
int xike_archive_verify_end(XikeArchiveReader *reader);
void xike_archive_reader_close(XikeArchiveReader *reader);

XikeArchiveWriter *xike_archive_writer(const char *path, const uint8_t *password,
                                       size_t password_size, int encrypted, int *result);
int xike_archive_add(XikeArchiveWriter *writer, const char *name, int64_t size);
int xike_archive_write(XikeArchiveWriter *writer, const uint8_t *data, size_t size);
int xike_archive_writer_close(XikeArchiveWriter *writer, int commit);

int xike_legacy_decrypt(const uint8_t *password, size_t password_size,
                        const uint8_t *salt, const uint8_t *nonce,
                        const uint8_t *ciphertext, size_t ciphertext_size,
                        uint8_t *plaintext, size_t *plaintext_size);

// The key is 32 bytes. AAD is the stable local attachment name.
XikeMediaReader *xike_media_reader(const char *path, const uint8_t *key,
                                   const char *aad, int *result);
int64_t xike_media_size(XikeMediaReader *reader);
int64_t xike_media_read(XikeMediaReader *reader, int64_t offset,
                        uint8_t *data, size_t size);
int64_t xike_matroska_duration(XikeMediaReader *reader);
void xike_media_reader_close(XikeMediaReader *reader);

XikeMediaWriter *xike_media_writer(const char *path, const uint8_t *key,
                                   const char *aad, int *result);
int xike_media_write(XikeMediaWriter *writer, const uint8_t *data, size_t size);
int xike_media_writer_close(XikeMediaWriter *writer, int commit);

#ifdef __cplusplus
}
#endif
