package foo

//import org.apache.spark.{SparkContext, SparkConf}
import org.apache.spark.{SparkContext, SparkConf}
import com.datastax.spark.connector._

object Main extends App {
  println("Hello, world")

  val conf = new SparkConf(true)
    .setMaster("local[2]")
    .set("spark.cassandra.connection.host", "127.0.0.1")
    .set("spark.executor.extraClassPath", "/Users/nils4tdd/dev/spark-cassandra-connector/spark-cassandra-connector-java/target/scala-2.11/spark-cassandra-connector-java-assembly-1.2.0-SNAPSHOT.jar")

  val sc = new SparkContext("spark://127.0.0.1:7077", "Excelsior", conf)

  val rdd = sc.cassandraTable("Excelsior", "facts")
  println(rdd.count)
  println(rdd.first)
  println(rdd.map(_.getInt("value")))


  //  val words = "./spark-cassandra-connector-demos/simple-demos/src/main/resources/data/words"
//
//  val CassandraHost = "127.0.0.1"
//
//  // Tell Spark the address of one Cassandra node:
//  val conf = new SparkConf(true)
//    .set("spark.cassandra.connection.host", "127.0.0.1")
//    .set("spark.cleaner.ttl", "3600")
//    .setMaster("127.0.0.1")
//    .setAppName(getClass.getSimpleName)
//
//  // Connect to the Spark cluster:
//  lazy val sc = new SparkContext(conf)

}