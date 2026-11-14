/* Deterministic host JNI check: no real interface or network reads. */
#define getifaddrs fixture_getifaddrs
#define freeifaddrs fixture_freeifaddrs
#include "../main/cpp/bionic_interfaces.c"
#undef getifaddrs
#undef freeifaddrs
#include <stdlib.h>

static struct ifaddrs rows[513];
static struct sockaddr_in ip4, mask4;
static struct sockaddr_in6 ip6, mask6;
static struct sockaddr packet;
int fixture_getifaddrs(struct ifaddrs** list) {
    const char* mode = getenv("NETPROBE_NATIVE_FIXTURE");
    if (mode && strcmp(mode, "denied") == 0) { errno = EACCES; *list = NULL; return -1; }
    memset(rows, 0, sizeof(rows));
    if (mode && strcmp(mode, "overflow") == 0) {
        for (int i = 0; i < 513; ++i) { rows[i].ifa_name = "fixture0"; if (i < 512) rows[i].ifa_next = &rows[i + 1]; }
        *list = rows;
        return 0;
    }
    memset(&ip4, 0, sizeof(ip4)); memset(&mask4, 0, sizeof(mask4));
    ip4.sin_family = mask4.sin_family = AF_INET;
    unsigned char addr4[4] = {192, 0, 2, 7}, bits4[4] = {255, 255, 255, 0};
    memcpy(&ip4.sin_addr, addr4, 4); memcpy(&mask4.sin_addr, bits4, 4);
    memset(&ip6, 0, sizeof(ip6)); memset(&mask6, 0, sizeof(mask6));
    ip6.sin6_family = mask6.sin6_family = AF_INET6; ip6.sin6_scope_id = 7;
    ip6.sin6_addr.s6_addr[0] = 0xfe; ip6.sin6_addr.s6_addr[1] = 0x80; ip6.sin6_addr.s6_addr[15] = 1;
    memset(mask6.sin6_addr.s6_addr, 255, 8);
    packet.sa_family = AF_PACKET;
    rows[0].ifa_name = "fixture0"; rows[0].ifa_flags = IFF_UP; rows[0].ifa_addr = (void*)&ip4; rows[0].ifa_netmask = (void*)&mask4;
    rows[1].ifa_name = "fixture0"; rows[1].ifa_flags = IFF_UP; rows[1].ifa_addr = (void*)&ip6; rows[1].ifa_netmask = (void*)&mask6;
    rows[2].ifa_name = "fixture0"; rows[2].ifa_flags = IFF_UP; rows[2].ifa_addr = &packet;
    rows[3].ifa_name = NULL;
    for (int i = 0; i < 3; ++i) rows[i].ifa_next = &rows[i + 1];
    *list = rows;
    return 0;
}
void fixture_freeifaddrs(struct ifaddrs* list) { (void)list; }
