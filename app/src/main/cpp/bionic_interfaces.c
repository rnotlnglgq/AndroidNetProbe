#include <jni.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netinet/in.h>
#include <errno.h>
#include <string.h>

#define MAX_ENTRIES 512
#define ENTRY_CLASS "com/rnotlnglgq/netprobe/probe/NativeIfEntry"
#define RESULT_CLASS "com/rnotlnglgq/netprobe/probe/NativeIfResult"

static jbyteArray bytes(JNIEnv* env, const void* data, size_t size) {
    if (data == NULL) return NULL;
    jbyteArray array = (*env)->NewByteArray(env, (jsize)size);
    if (array != NULL && size != 0) (*env)->SetByteArrayRegion(env, array, 0, (jsize)size, data);
    return array;
}

static jobject result(JNIEnv* env, jclass result_class, jmethodID ctor, int error, jobjectArray entries) {
    if ((*env)->ExceptionCheck(env)) return NULL;
    return (*env)->NewObject(env, result_class, ctor, (jint)error, entries);
}

JNIEXPORT jobject JNICALL
Java_com_rnotlnglgq_netprobe_probe_BionicBridge_captureNative(JNIEnv* env, jobject self) {
    (void)self;
    jclass entry_class = (*env)->FindClass(env, ENTRY_CLASS);
    if (entry_class == NULL) return NULL;
    jclass result_class = (*env)->FindClass(env, RESULT_CLASS);
    if (result_class == NULL) return NULL;
    jmethodID entry_ctor = (*env)->GetMethodID(env, entry_class, "<init>", "([BJI[B[BJ)V");
    if (entry_ctor == NULL) return NULL;
    jmethodID result_ctor = (*env)->GetMethodID(env, result_class, "<init>", "(I[L" ENTRY_CLASS ";)V");
    if (result_ctor == NULL) return NULL;

    struct ifaddrs* list = NULL;
    if (getifaddrs(&list) != 0) {
        int saved_errno = errno != 0 ? errno : EIO;
        return result(env, result_class, result_ctor, saved_errno, NULL);
    }
    size_t count = 0;
    int error = 0;
    for (const struct ifaddrs* item = list; item != NULL; item = item->ifa_next) {
        if (item->ifa_name == NULL) continue;
        if (strnlen(item->ifa_name, IFNAMSIZ + 1) > IFNAMSIZ) { error = ENAMETOOLONG; break; }
        if (++count > MAX_ENTRIES) { error = EOVERFLOW; break; }
    }
    if (error != 0) {
        freeifaddrs(list);
        return result(env, result_class, result_ctor, error, NULL);
    }
    jobjectArray entries = (*env)->NewObjectArray(env, (jsize)count, entry_class, NULL);
    if (entries == NULL) { freeifaddrs(list); return NULL; }
    jsize index = 0;
    for (const struct ifaddrs* item = list; item != NULL; item = item->ifa_next) {
        if (item->ifa_name == NULL) continue;
        if ((*env)->PushLocalFrame(env, 6) != 0) { freeifaddrs(list); return NULL; }
        int family = 0;
        size_t width = 0;
        const void* address = NULL;
        const void* mask = NULL;
        jlong scope_id = 0;
        if (item->ifa_addr != NULL && item->ifa_addr->sa_family == AF_INET) {
            family = 4; width = 4;
            address = &((const struct sockaddr_in*)item->ifa_addr)->sin_addr;
            if (item->ifa_netmask != NULL && item->ifa_netmask->sa_family == AF_INET)
                mask = &((const struct sockaddr_in*)item->ifa_netmask)->sin_addr;
        } else if (item->ifa_addr != NULL && item->ifa_addr->sa_family == AF_INET6) {
            family = 6; width = 16;
            const struct sockaddr_in6* sin6 = (const struct sockaddr_in6*)item->ifa_addr;
            address = &sin6->sin6_addr;
            scope_id = (jlong)sin6->sin6_scope_id;
            if (item->ifa_netmask != NULL && item->ifa_netmask->sa_family == AF_INET6)
                mask = &((const struct sockaddr_in6*)item->ifa_netmask)->sin6_addr;
        }
        // Do not inspect or copy AF_PACKET / hardware addresses.
        jbyteArray name = bytes(env, item->ifa_name, strlen(item->ifa_name));
        jbyteArray ip = bytes(env, address != NULL ? address : "", width);
        jbyteArray netmask = bytes(env, mask, width);
        if ((*env)->ExceptionCheck(env)) { (*env)->PopLocalFrame(env, NULL); freeifaddrs(list); return NULL; }
        jobject entry = (*env)->NewObject(env, entry_class, entry_ctor, name, (jlong)item->ifa_flags,
            (jint)family, ip, netmask, scope_id);
        if ((*env)->ExceptionCheck(env)) { (*env)->PopLocalFrame(env, NULL); freeifaddrs(list); return NULL; }
        if (entry != NULL) (*env)->SetObjectArrayElement(env, entries, index++, entry);
        (*env)->PopLocalFrame(env, NULL);
        if ((*env)->ExceptionCheck(env)) { freeifaddrs(list); return NULL; }
    }
    freeifaddrs(list);
    return result(env, result_class, result_ctor, 0, entries);
}
