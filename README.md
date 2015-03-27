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

added config to `conf/spark-env.sh`

    export SPARK_WORKER_MEMORY=2g
    export SPARK_WORKER_INSTANCES=2
    export SPARK_WORKER_DIR=/Users/nils4tdd/dev/spark_worker_dir

added config to `conf/spark-defaults.sh`

    # Default system properties included when running spark-submit.
    # This is useful for setting default environmental settings.

    # Example:
    spark.master                     spark://127.0.0.1:7077
    spark.driver.memory              2g

start the cluster

    sbin/start-master.sh
    ./bin/spark-class org.apache.spark.deploy.worker.Worker spark://127.0.0.1:7077
    ./bin/spark-class org.apache.spark.deploy.worker.Worker spark://127.0.0.1:7077
    ./bin/spark-class org.apache.spark.deploy.worker.Worker spark://127.0.0.1:7077

    # for 3 slaves

this can be checked on http://localhost:8080 if its working
now the shell can be started:

    MASTER=spark://127.0.0.1:7077 bin/spark-shell

or:

    git clone https://github.com/datastax/spark-cassandra-connector.git
    cd spark-cassandra-connector
    sbt/sbt assembly
    ~/Downloads/spark-1.2.1/bin/spark-shell \ 
        --jars ~/spark-cassandra-connector/spark-cassandra-connector/target/scala-2.10/connector-assembly-1.2.0-SNAPSHOT.jar 

in the shell:

    sc.stop
    import com.datastax.spark.connector._
    import org.apache.spark.SparkContext
    import org.apache.spark.SparkContext._
    import org.apache.spark.SparkConf
    val conf = new SparkConf(true).set("spark.cassandra.connection.host", "127.0.0.1")
    val sc = new SparkContext("spark://127.0.0.1:7077", "test", conf)

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

this gave the error that something with akka is wrong:
https://groups.google.com/a/lists.datastax.com/forum/#!topic/spark-connector-user/UqCYeUpgGCU

its maybe something about versioning.
other errors came in the workers logs:

org.apache.spark.deploy.Command; local class incompatible: stream classdesc serialVersionUID = -7098307370860582211, local class serialVersionUID = -333531271946754762

next test would be to use scala 2.10 and not scala 2.11
