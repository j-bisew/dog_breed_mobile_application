import sqlite3
import http.server
import dbHelperFunctions


METODS = {
    '/requestSalt': dbHelperFunctions.requestSaltPathHandler,
    '/verifyUsernamePassword': dbHelperFunctions.verifyUsernamePasswordPathHandler,
    '/isUsernameOrEmailTaken': dbHelperFunctions.isUsernameOrEmailTakenPathHandler,
    '/registerUser': dbHelperFunctions.registerUserPathHandler,
    '/getDogRaceInfo': dbHelperFunctions.getDogRaceInfoPathHandler,
    '/addDogRace': dbHelperFunctions.addRacePathHandler,
    '/incrementDogRaceQuestionedCount': dbHelperFunctions.incrementDogRaceQuestionedCountPathHandler,
}

class DatabaseRequestHandler(http.server.BaseHTTPRequestHandler):
    wbufsize = 0
    disable_nagle_algorithm = True

    def do_GET(self):
        if self.path == '/':
            self.send_response(200)
            self.send_header('Content-type', 'text/html')
            self.end_headers()
            self.wfile.write(b'Welcome to the Database Handler Server!')
        else:
            self.send_response(404)
            self.end_headers()
            self.wfile.write(b'Not Found')
    
    def do_POST(self):
        if self.path in METODS:
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

def run(server_class=http.server.ThreadingHTTPServer, handler_class=DatabaseRequestHandler, port=8020):
    server_address = ('', port)
    httpd = server_class(server_address, handler_class)
    print(f'Starting database handler server on port {port}...')
    httpd.serve_forever()

if __name__ == '__main__':
    run()
