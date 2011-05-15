import time
from fcpconnection import FCPConnection, PolledSocket
from fcpclient import FCPClient

def insert_files(host, port, names):
    socket = PolledSocket(host, port)
    connection = FCPConnection(socket, True)

    inserts = []
    for name in names:
        client = FCPClient(connection)
        client.in_params.default_fcp_params['MaxRetries'] = 3
        client.in_params.default_fcp_params['PriorityClass'] = 1

        inserts.append(client)
        client.in_params.async = True
        parts = name.split('/')

        client.put_file('CHK@' + '/' + parts[-1], name)
         # Hmmmm... Ugly. Add FCPConnection.wait_until_upload_finishes() ?
        while connection.is_uploading():
            socket.poll()
            time.sleep(.25)

    while min([insert.is_finished() for insert in inserts]) == False:
        if not socket.poll():
            break
        time.sleep(.25)

    uris = []
    for insert in inserts:
        if insert.response == None or len(insert.response) < 2 or insert.response[0] <> 'PutSuccessful':
            uris.append(None)
            continue

        uris.append(insert.response[1]['URI'])

    return uris

FCP_HOST = '127.0.0.1'
FCP_PORT = 19481
FILES = ['/tmp/0.txt', '/tmp/1.txt', '/tmp/2.txt' ]

def main():
    uris = insert_files(FCP_HOST, FCP_PORT, FILES)
    for name, uri in zip(FILES, uris):
        print "---"
        print name
        print uri
    print "---"

#main()
