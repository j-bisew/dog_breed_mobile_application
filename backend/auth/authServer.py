import http.server
import helperFunctions
import json

POST_PATHS = {
    '/login': helperFunctions.loginPathHandler,
    '/register': helperFunctions.registerPathHandler,
    '/verifyToken': helperFunctions.verifyTokenPathHandler
}

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
    
    def do_POST(self):
        if self.path in POST_PATHS:
            handler = POST_PATHS[self.path]
            if handler:
                handler(self)
            else:
                self.send_response(501)
                self.end_headers()
                self.wfile.write(b'Not Implemented')


        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found')
            

def run(server_class=http.server.ThreadingHTTPServer, handler_class=AuthRequestHandler, port=8010):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print(f'Starting auth server on port {port}...')
    httpd.serve_forever()

if __name__ == '__main__':
    run()