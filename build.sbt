name := "td_spark_cassandra"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  "com.datastax.spark"      %% "spark-cassandra-connector" % "1.2.0-alpha3",
  "org.apache.cassandra"    %  "cassandra-thrift"       % "2.1.3",
  "org.apache.cassandra"    %  "cassandra-clientutil"   % "2.1.3",
  "com.datastax.cassandra"  %  "cassandra-driver-core"  % "2.1.3",
  "org.apache.spark"        %% "spark-core"            % "1.2.0"
)