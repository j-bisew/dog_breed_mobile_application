import http.server
import helperFunctions

class AuthRequestHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write(b'Welcome to the Auth Server!')
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found')
    
def run(server_class=http.server.HTTPServer, handler_class=AuthRequestHandler, port=8010):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print(f'Starting auth server on port {port}...')
    httpd.serve_forever()

if __name__ == '__main__':
    run()