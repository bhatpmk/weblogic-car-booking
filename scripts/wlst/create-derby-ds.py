# WLST script: Create a WebLogic JDBC Data Source for Derby (Embedded)
# Usage (online WLST):
#   ${WL_HOME}/common/bin/wlst.sh sample/wlst/create-derby-ds.py \ 
#     --adminUrl t3://localhost:7001 --user weblogic --password welcome1 \
#     --dsName CarBookingDS --jndiName jdbc/CarBookingDS \
#     --driver org.apache.derby.jdbc.EmbeddedDriver \
#     --url jdbc:derby:/absolute/path/to/milesofsmiles;create=true \
#     --target AdminServer
#
# Notes:
# - This configures an Embedded Derby driver. The Derby database files will be opened inside the WebLogic server JVM.
# - Ensure the URL points to a writable location on the server host (for example, <DOMAIN_HOME>/derby/milesofsmiles).

from java.lang import String
import jarray
import sys

def parse_args(argv):
    args = {
        'adminUrl': None,
        'user': None,
        'password': None,
        'dsName': 'CarBookingDS',
        'jndiName': 'jdbc/CarBookingDS',
        'driver': 'org.apache.derby.jdbc.EmbeddedDriver',
        'url': 'jdbc:derby:milesofsmiles;create=true',
        'target': 'AdminServer',
        'dbUser': None,
        'dbPassword': None
    }
    i = 0
    while i < len(argv):
        key = argv[i]
        val = None
        if i + 1 < len(argv):
            val = argv[i+1]
        if key in ['--adminUrl', '-adminUrl']:
            args['adminUrl'] = val; i += 2
        elif key in ['--user', '-user', '--username', '-username']:
            args['user'] = val; i += 2
        elif key in ['--password', '-password', '--pass', '-pass']:
            args['password'] = val; i += 2
        elif key in ['--dsName', '-dsName']:
            args['dsName'] = val; i += 2
        elif key in ['--jndiName', '-jndiName']:
            args['jndiName'] = val; i += 2
        elif key in ['--driver', '-driver']:
            args['driver'] = val; i += 2
        elif key in ['--url', '-url']:
            args['url'] = val; i += 2
        elif key in ['--target', '-target']:
            args['target'] = val; i += 2
        elif key in ['--dbUser', '-dbUser']:
            args['dbUser'] = val; i += 2
        elif key in ['--dbPassword', '-dbPassword']:
            args['dbPassword'] = val; i += 2
        elif key in ['-h', '--help']:
            print_help_and_exit()
        else:
            # Skip unknown flag or stray value
            i += 1
    missing = [k for k in ['adminUrl', 'user', 'password'] if not args[k]]
    if missing:
        print('Missing required args: %s' % ', '.join(missing))
        print_help_and_exit()
    return args

def print_help_and_exit():
    print('Usage: wlst create-derby-ds.py --adminUrl t3://host:port --user <user> --password <pwd> [--dsName CarBookingDS] [--jndiName jdbc/CarBookingDS]')
    print('       [--driver org.apache.derby.jdbc.EmbeddedDriver] [--url jdbc:derby:/absolute/path/to/milesofsmiles;create=true] [--target AdminServer] [--dbUser <db user>] [--dbPassword <db password>]')
    exit(1)

def ensure_no_existing(dsName):
    # Ensure we are in the config MBean tree so cmo refers to DomainMBean
    domainConfig()
    existing = cmo.lookupJDBCSystemResource(dsName)
    if existing is not None:
        print('JDBCSystemResource %s already exists. Nothing to do.' % dsName)
        return True
    return False

def main(argv):
    args = parse_args(argv)

    print('Connecting to %s ...' % args['adminUrl'])
    connect(args['user'], args['password'], args['adminUrl'])

    try:
        domainConfig()

        if ensure_no_existing(args['dsName']):
            disconnect()
            return

        edit()
        startEdit()
        cd('/')

        print('Creating JDBCSystemResource with name ' + args['dsName'])
        jdbcSR = create(args['dsName'], 'JDBCSystemResource')

        theJDBCResource = jdbcSR.getJDBCResource()
        theJDBCResource.setName(args['jndiName'])

        # DataSource params (JNDI)
        dsParams = theJDBCResource.getJDBCDataSourceParams()
        dsParams.setJNDINames(jarray.array([String(args['jndiName'])], String))

        # Driver params
        drvParams = theJDBCResource.getJDBCDriverParams()
        drvParams.setDriverName(args['driver'])
        drvParams.setUrl(args['url'])

        # Set encrypted password only if provided (production-safe)
        if args['dbPassword'] is not None:
            enc = encrypt(args['dbPassword'])
            drvParams.setPasswordEncrypted(enc)

        # Driver properties (set user only if provided)
        props = drvParams.getProperties()
        if args['dbUser'] is not None:
            if props.lookupProperty('user') is None:
                props.createProperty('user')
            props.lookupProperty('user').setValue(args['dbUser'])

        # Connection pool params
        poolParams = theJDBCResource.getJDBCConnectionPoolParams()
        poolParams.setTestConnectionsOnReserve(True)
        poolParams.setTestTableName('SYS.SYSTABLES')

        # Target the resource to the desired server
        targetBean = cmo.lookupServer(args['target'])
        if targetBean is not None:
            print('Targeting JDBCSystemResource %s to Server %s' % (args['dsName'], args['target']))
            jdbcSR.addTarget(targetBean)
        else:
            print('Server %s not found, using assign() fallback' % args['target'])
            save()
            assign('JDBCSystemResource', args['dsName'], 'Target', args['target'])

        save()
        activate(block='true')
        print('JDBC Data Source %s created and targeted to %s' % (args['dsName'], args['target']))

    except:
        print('Error occurred; attempting to undo edits')
        dumpStack()
        try:
            cancelEdit('y')
        except:
            pass
        raise
    finally:
        try:
            disconnect()
        except:
            pass

if __name__ == '__main__' or True:
    main(sys.argv[1:])
