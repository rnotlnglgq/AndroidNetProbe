#!/usr/bin/env python3
"""Owned endpoint template. No proxy trust, telemetry, storage or access logging."""
import argparse
import datetime
import http.server
import ipaddress
import json
import socket
import ssl
import uuid


def observed_payload(tcp_peer):
    address = ipaddress.ip_address(tcp_peer)
    return {
        "observedIp": str(address),
        "ipFamily": f"IPv{address.version}",
        "serverTimeUtc": datetime.datetime.now(datetime.timezone.utc).isoformat().replace("+00:00", "Z"),
        "requestId": str(uuid.uuid4()),
    }


class ProbeHandler(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path != "/probe":
            self.send_error(404)
            return
        # Deliberately ignore Forwarded/X-Forwarded-For and all client-controlled IP headers.
        body = json.dumps(observed_payload(self.client_address[0])).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def log_message(self, fmt, *args):
        pass


class IPv6Server(http.server.ThreadingHTTPServer):
    address_family = socket.AF_INET6

    def server_bind(self):
        self.socket.setsockopt(socket.IPPROTO_IPV6, socket.IPV6_V6ONLY, 1)
        super().server_bind()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--family", choices=["IPv4", "IPv6"], required=True)
    parser.add_argument("--bind", required=True)
    parser.add_argument("--port", type=int, default=8443)
    parser.add_argument("--certfile", required=True)
    parser.add_argument("--keyfile", required=True)
    args = parser.parse_args()
    if ipaddress.ip_address(args.bind).version != int(args.family[-1]):
        parser.error("bind address and family disagree")
    server_type = IPv6Server if args.family == "IPv6" else http.server.ThreadingHTTPServer
    server = server_type((args.bind, args.port), ProbeHandler)
    server.daemon_threads = True
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(args.certfile, args.keyfile)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    try:
        server.serve_forever()
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
