-- ij bootstrap to initialize Miles of Smiles Derby database
-- Usage with ij (embedded):
--   java -cp $DERBY_HOME/lib/derbytools.jar:$DERBY_HOME/lib/derby.jar org.apache.derby.tools.ij derby-init.sql

-- Default: file-based DB, update the path accordingly
connect 'jdbc:derby:/absolute/path/to/milesofsmiles;create=true';

-- Run DDL and seed data
run 'derby-schema.sql';
run 'derby-data.sql';

disconnect;
exit;
