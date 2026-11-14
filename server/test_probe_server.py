import http.client
import http.server
import json
import threading
import unittest
from probe_server import ProbeHandler, observed_payload


class ProbeServerTest(unittest.TestCase):
    def test_payload_family_and_request_ids(self):
        self.assertEqual("IPv6", observed_payload("2001:db8::1")["ipFamily"])
        self.assertNotEqual(observed_payload("192.0.2.1")["requestId"], observed_payload("192.0.2.1")["requestId"])

    def test_tcp_peer_is_used_even_with_forged_proxy_headers(self):
        server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), ProbeHandler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            client = http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=2)
            client.request("GET", "/probe", headers={"X-Forwarded-For": "192.0.2.99", "Forwarded": "for=192.0.2.98"})
            response = client.getresponse()
            payload = json.loads(response.read())
            self.assertEqual(200, response.status)
            self.assertEqual("127.0.0.1", payload["observedIp"])
            self.assertEqual("no-store", response.getheader("Cache-Control"))
            client.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join(timeout=2)


if __name__ == "__main__":
    unittest.main()
