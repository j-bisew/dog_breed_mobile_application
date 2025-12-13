import http.server
import endpointsHelperFunctions
import json

METODS = {
    '/registerUser': endpointsHelperFunctions.registerUserPathHandler,
    '/loginUser': endpointsHelperFunctions.loginUserPathHandler,
    '/getDogBreedInfo': endpointsHelperFunctions.getDogBreedInfoPathHandler,
    '/submitDogBreedFeedback': endpointsHelperFunctions.submitDogBreedFeedbackPathHandler,
}

AUTHORIZATION_REQUIRED_PATHS = [
    '/submitDogBreedFeedback',
    '/getDogBreedInfo',
]

class EndpointsRequestHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path == '/':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write(b'Welcome to the Endpoints Server!')
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found')
    def do_POST(self):
        if self.path in METODS:
            if self.path in AUTHORIZATION_REQUIRED_PATHS:
                isAuthorized = endpointsHelperFunctions.authorizationCheck(self)
                if not isAuthorized:
                    self.send_response(401)
                    self.end_headers()
                    self.wfile.write(b'Unauthorized')
                    return


            handler = METODS[self.path]
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

def run(server_class=http.server.HTTPServer, handler_class=EndpointsRequestHandler, port=8000):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print(f'Starting endpoints server on port {port}...')
    httpd.serve_forever()

if __name__ == "__main__":
    run()