# td_cassandra_spark

### cassandra

First install and start cassandra:

    #http://christopher-batey.blogspot.de/2013/05/installing-cassandra-on-mac-os-x.html
    brew install cassandra
    #run it with:
    export JAVA_HOME=`/usr/libexec/java_home -v '1.8*'`
    /usr/local/Cellar/cassandra/2.1.0/bin/cassandra

the actual cassandra shell can be reached with:

    cqlsh

the tested table for dimensions is:

    create table Excelsior.facts (
        type text,
        value float,
        dimensions map<text,text>,
        year int,
        month int,
        day int,
        PRIMARY KEY (type, year, month, day));

    #drop index Excelsior.facts_dimensions_idx;
    CREATE INDEX ON Excelsior.facts (keys(dimensions));

    insert into Excelsior.facts (type, value, dimensions, year, month, day) values
      ('youtube_visit', 12, {'ccr':'Brand','FR':'Market'}, 2015, 02, 02);
    insert into Excelsior.facts (type, value, dimensions, year, month, day) values
      ('youtube_visit', 12, {'ccr':'Brand','DE':'Market'}, 2015, 02, 02);


    SELECT value
    FROM Excelsior.facts
    WHERE
      type = 'youtube_visit' and
      dimensions CONTAINS KEY 'ccr';

### spark

checked out the http://spark.incubator.apache.org/screencasts/1-first-steps-with-spark.html and the following
I build spark from source there.

Followed the steps from http://pulasthisupun.blogspot.de/2013/11/how-to-set-up-apache-spark-cluster-in.html which were:

added config to config/spark-env.sh

    export SPARK_WORKER_MEMORY=2g
    export SPARK_WORKER_INSTANCES=2
    export SPARK_WORKER_DIR=/Users/nils4tdd/dev/spark_worker_dir

added config to config/spark-default.sh

    # Default system properties included when running spark-submit.
    # This is useful for setting default environmental settings.

    # Example:
    spark.master                     spark://127.0.0.1:7077
    spark.driver.memory              2g

start the cluster

    sbin/spark-master.sh
    ./bin/spark-class org.apache.spark.deploy.worker.Worker spark://127.0.0.1:7077
    ./bin/spark-class org.apache.spark.deploy.worker.Worker spark://127.0.0.1:7077
    ./bin/spark-class org.apache.spark.deploy.worker.Worker spark://127.0.0.1:7077

    # for 3 slaves

this can be checked on http://localhost:8080 if its working
now the shell can be started:

    MASTER=spark://127.0.0.1:7077 bin/spark-shell

running this example shows the effect:

    val NUM_SAMPLES = 100000000
    val count = sc.parallelize(1 to NUM_SAMPLES).map{i =>
      val x = Math.random * 2 - 1
      val y = Math.random * 2 - 1
      if (x * x + y * y < 1) 1.0 else 0.0
    }.reduce(_ + _)
    println("Pi is roughly " + 4 * count / NUM_SAMPLES)

try to make this run with the build tool: `sbt`.

https://github.com/datastax/spark-cassandra-connector/blob/master/spark-cassandra-connector-demos/simple-demos/src/main/scala/com/datastax/spark/connector/demo/DemoApp.scala
