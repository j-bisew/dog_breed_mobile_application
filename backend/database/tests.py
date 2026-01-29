import unittest
from unittest.mock import MagicMock, patch
import json
import sys
import os

# Add parent directory to path to import modules
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

# We need to mock sqlite3.connect BEFORE importing dbHelperFunctions
# because it connects to DB at module level
with patch('sqlite3.connect') as mock_connect:
    mock_connect.return_value = MagicMock()
    import dbHelperFunctions
    import sqlite3 # Import real sqlite3 to get exceptions if needed, but we mocked it...
    # If we mocked sys.modules['sqlite3'], we lose IntegrityError.
    # Instead, let's restore sqlite3 but patch connect.
    
    # Actually, simplistic approach: let it connect (it creates file if missing),
    # or rely on the patch context above IF the import happens inside it.
    pass

class TestDatabaseFunctions(unittest.TestCase):

    def setUp(self):
        self.mock_handler = MagicMock()
        self.mock_handler.headers = {'Content-Length': '0'}
        self.mock_handler.rfile.read.return_value = b'{}'
        self.mock_handler.wfile = MagicMock()
    
    # We patch the GLOBAL variables in dbHelperFunctions that hold the cursor/conn
    @patch('dbHelperFunctions.authDBCursor')
    def test_requestSaltPathHandler_success(self, mock_cursor):
        # Setup
        username = 'testuser'
        expected_salt = 'somesalt'
        post_data = json.dumps({'usernameData': {'username': username}}).encode('utf-8')
        
        self.mock_handler.headers = {'Content-Length': str(len(post_data))}
        self.mock_handler.rfile.read.return_value = post_data
        
        mock_cursor.fetchone.return_value = (expected_salt,)

        # Execute
        dbHelperFunctions.requestSaltPathHandler(self.mock_handler)

        # Verify
        mock_cursor.execute.assert_called_once()
        self.mock_handler.send_response.assert_called_with(200)
        
        # Verify response body
        args, _ = self.mock_handler.wfile.write.call_args
        response_json = json.loads(args[0].decode('utf-8'))
        self.assertEqual(response_json['salt'], expected_salt)

    @patch('dbHelperFunctions.authDBCursor')
    def test_requestSaltPathHandler_not_found(self, mock_cursor):
        post_data = json.dumps({'usernameData': {'username': 'unknown'}}).encode('utf-8')
        self.mock_handler.headers = {'Content-Length': str(len(post_data))}
        self.mock_handler.rfile.read.return_value = post_data
        
        mock_cursor.fetchone.return_value = None

        dbHelperFunctions.requestSaltPathHandler(self.mock_handler)

        self.mock_handler.send_response.assert_called_with(404)

    @patch('dbHelperFunctions.authDBCursor')
    def test_verifyUsernamePasswordPathHandler_success(self, mock_cursor):
        username = 'testuser'
        password = 'hashedpassword'
        post_data = json.dumps({'loginData': {'username': username, 'password': password}}).encode('utf-8')
        
        self.mock_handler.headers = {'Content-Length': str(len(post_data))}
        self.mock_handler.rfile.read.return_value = post_data
        
        mock_cursor.fetchone.return_value = (password,)

        dbHelperFunctions.verifyUsernamePasswordPathHandler(self.mock_handler)

        self.mock_handler.send_response.assert_called_with(200)

    @patch('dbHelperFunctions.authDBCursor')
    def test_verifyUsernamePasswordPathHandler_wrong_password(self, mock_cursor):
        username = 'testuser'
        password = 'wrongpassword'
        post_data = json.dumps({'loginData': {'username': username, 'password': password}}).encode('utf-8')
        
        self.mock_handler.headers = {'Content-Length': str(len(post_data))}
        self.mock_handler.rfile.read.return_value = post_data
        
        mock_cursor.fetchone.return_value = ('correcthash',)

        dbHelperFunctions.verifyUsernamePasswordPathHandler(self.mock_handler)

        self.mock_handler.send_response.assert_called_with(401)

    @patch('dbHelperFunctions.authDBCursor')
    @patch('dbHelperFunctions.authDBConnection')
    def test_registerUserPathHandler_success(self, mock_conn, mock_cursor):
        data = {
            'registrationData': {
                'name': 'Test',
                'username': 'newuser',
                'email': 'test@example.com',
                'password_hash': 'hash',
                'salt': 'salt'
            }
        }
        post_data = json.dumps(data).encode('utf-8')
        self.mock_handler.headers = {'Content-Length': str(len(post_data))}
        self.mock_handler.rfile.read.return_value = post_data

        dbHelperFunctions.registerUserPathHandler(self.mock_handler)

        self.mock_handler.send_response.assert_called_with(201)
        mock_cursor.execute.assert_called()
        mock_conn.commit.assert_called()

    @patch('dbHelperFunctions.raceDBCursor')
    @patch('os.path.exists')
    @patch('builtins.open')
    def test_getDogRaceInfoPathHandler_by_id(self, mock_file, mock_exists, mock_race_cursor):
        # Setup
        data = {'raceRequestData': {'raceId': 1}}
        post_data = json.dumps(data).encode('utf-8')
        self.mock_handler.headers = {'Content-Length': str(len(post_data))}
        self.mock_handler.rfile.read.return_value = post_data
        
        # Mock DB return: id, name, folderName
        mock_race_cursor.fetchone.return_value = (1, 'Poodle', 'poodle_folder')
        
        # Mock file existence
        mock_exists.return_value = True

        # Mock open to handle binary and text files
        def side_effect(filename, mode='r', encoding=None):
            m = MagicMock()
            if 'rb' in mode:
                m.read.return_value = b'fakeimagebytes'
            else:
                m.read.return_value = "Breed: Poodle\nDescription: A curly dog."
            m.__enter__.return_value = m
            return m
        
        mock_file.side_effect = side_effect

        # Execute
        dbHelperFunctions.getDogRaceInfoPathHandler(self.mock_handler)

        # Verify
        self.mock_handler.send_response.assert_called_with(200)
        # Check that we tried to read the summary file
        self.assertTrue(mock_file.called)
        
    @patch('dbHelperFunctions.raceDBCursor')
    @patch('dbHelperFunctions.raceDBConnection')
    def test_addRacePathHandler_success(self, mock_conn, mock_cursor):
        data = {'raceData': {'name': 'Bulldog', 'folderName': 'bulldog_folder'}}
        post_data = json.dumps(data).encode('utf-8')
        self.mock_handler.headers = {'Content-Length': str(len(post_data))}
        self.mock_handler.rfile.read.return_value = post_data

        dbHelperFunctions.addRacePathHandler(self.mock_handler)

        self.mock_handler.send_response.assert_called_with(201)
        mock_cursor.execute.assert_called()
        mock_conn.commit.assert_called()

if __name__ == '__main__':
    unittest.main()
