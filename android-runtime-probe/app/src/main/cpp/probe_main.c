#include <errno.h>
#include <locale.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/utsname.h>
#include <unistd.h>

#if defined(__aarch64__)
#define RAVA_ABI "arm64-v8a"
#elif defined(__arm__)
#define RAVA_ABI "armeabi-v7a"
#elif defined(__x86_64__)
#define RAVA_ABI "x86_64"
#elif defined(__i386__)
#define RAVA_ABI "x86"
#else
#define RAVA_ABI "unknown"
#endif

static void json_string(const char *value) {
    const unsigned char *cursor = (const unsigned char *) (value == NULL ? "" : value);
    putchar('"');
    while (*cursor != '\0') {
        switch (*cursor) {
            case '\\': fputs("\\\\", stdout); break;
            case '"': fputs("\\\"", stdout); break;
            case '\n': fputs("\\n", stdout); break;
            case '\r': fputs("\\r", stdout); break;
            case '\t': fputs("\\t", stdout); break;
            default:
                if (*cursor < 0x20) {
                    fprintf(stdout, "\\u%04x", *cursor);
                } else {
                    putchar(*cursor);
                }
        }
        cursor++;
    }
    putchar('"');
}

int main(int argc, char **argv) {
    struct utsname system_info;
    char cwd[1024];
    const char *locale_name = setlocale(LC_ALL, "");
    long page_size = sysconf(_SC_PAGESIZE);
    int uname_result = uname(&system_info);

    if (getcwd(cwd, sizeof(cwd)) == NULL) {
        snprintf(cwd, sizeof(cwd), "unavailable: %s", strerror(errno));
    }

    fputs("{\"probe\":\"rava-native\",\"version\":1,\"abi\":", stdout);
    json_string(RAVA_ABI);
    fprintf(stdout, ",\"pageSizeBytes\":%ld,\"pid\":%ld,\"uid\":%ld,\"gid\":%ld",
            page_size, (long) getpid(), (long) getuid(), (long) getgid());
    fputs(",\"cwd\":", stdout);
    json_string(cwd);
    fputs(",\"locale\":", stdout);
    json_string(locale_name);
    fputs(",\"unicode\":", stdout);
    json_string("فارسی ✓");
    fputs(",\"kernel\":", stdout);
    json_string(uname_result == 0 ? system_info.release : "unavailable");
    fprintf(stdout, ",\"argc\":%d,\"argv\":[", argc);
    for (int index = 0; index < argc; index++) {
        if (index > 0) putchar(',');
        json_string(argv[index]);
    }
    fputs("]}\n", stdout);
    return page_size > 0 && uname_result == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
