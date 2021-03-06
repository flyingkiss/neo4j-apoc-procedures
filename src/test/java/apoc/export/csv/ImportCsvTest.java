package apoc.export.csv;

import apoc.util.TestUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Result;
import org.neo4j.internal.kernel.api.exceptions.KernelException;
import org.neo4j.test.TestGraphDatabaseFactory;

import java.io.File;
import java.io.IOException;
import java.util.AbstractMap;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static apoc.util.MapUtil.map;
import static org.junit.Assert.assertEquals;

public class ImportCsvTest {

    private GraphDatabaseService db;

    final Map<String, String> testCsvs = Collections
            .unmodifiableMap(Stream.of(
                    new AbstractMap.SimpleEntry<>("array", ":ID|name:STRING[]\n" +
                            "1|John;Bob;Alice\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-basic-affiliated-with", ":START_ID,:END_ID\n" +
                            "1,3\n" +
                            "2,4\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-basic-companies", "companyId:ID,name:STRING\n" +
                            "4,Neo4j\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-basic-persons", "personId:ID,name:STRING\n" +
                            "1,John\n" +
                            "2,Jane\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-basic-unis", "uniId:ID,name:STRING\n" +
                            "3,TU Munich\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-idspaces-affiliated-with", ":START_ID(Person),:END_ID(Organisation)\n" +
                            "1,1\n" +
                            "2,2\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-idspaces-companies", "companyId:ID(Organisation),name:STRING\n" +
                            "2,Neo4j\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-idspaces-persons", "personId:ID(Person),name:STRING\n" +
                            "1,John\n" +
                            "2,Jane\n"),
                    new AbstractMap.SimpleEntry<>("custom-ids-idspaces-unis", "uniId:ID(Organisation),name:STRING\n" +
                            "1,TU Munich\n"),
                    new AbstractMap.SimpleEntry<>("id-idspaces", ":ID(Person)|name:STRING\n" +
                            "1|John\n" +
                            "2|Jane\n"),
                    new AbstractMap.SimpleEntry<>("id", "id:ID|name:STRING\n" +
                            "1|John\n" +
                            "2|Jane\n"),
                    new AbstractMap.SimpleEntry<>("ignore-nodes", ":ID|firstname:STRING|lastname:IGNORE|age:INT\n" +
                            "1|John|Doe|25\n" +
                            "2|Jane|Doe|26\n"),
                    new AbstractMap.SimpleEntry<>("ignore-relationships", ":START_ID|:END_ID|prop1:IGNORE|prop2:INT\n" +
                            "1|2|a|3\n" +
                            "2|1|b|6\n"),
                    new AbstractMap.SimpleEntry<>("label", ":ID|:LABEL|name:STRING\n" +
                            "1|Student;Employee|John\n"),
                    new AbstractMap.SimpleEntry<>("knows", ":START_ID,:END_ID,since:INT\n" +
                            "1,2,2016\n" +
                            "10,11,2014\n" +
                            "11,12,2013"),
                    new AbstractMap.SimpleEntry<>("persons", ":ID,name:STRING,speaks:STRING[]\n" +
                            "1,John,\"en,fr\"\n" +
                            "2,Jane,\"en,de\""),
                    new AbstractMap.SimpleEntry<>("quoted", "id:ID|:LABEL|name:STRING\n" +
                            "'1'|'Student:Employee'|'John'\n"),
                    new AbstractMap.SimpleEntry<>("rel-on-ids-idspaces", ":START_ID(Person)|:END_ID(Person)|since:INT\n" +
                            "1|2|2016\n"),
                    new AbstractMap.SimpleEntry<>("rel-on-ids", "x:START_ID|:END_ID|since:INT\n" +
                            "1|2|2016\n"),
                    new AbstractMap.SimpleEntry<>("rel-type", ":START_ID|:END_ID|:TYPE|since:INT\n" +
                            "1|2|FRIENDS_WITH|2016\n" +
                            "2|1||2016\n"),
                    new AbstractMap.SimpleEntry<>("typeless", ":ID|name\n" +
                            "1|John\n" +
                            "2|Jane\n")
            ).collect(Collectors.toMap(AbstractMap.SimpleEntry::getKey, AbstractMap.SimpleEntry::getValue)));

    @Before
    public void setUp() throws IOException, KernelException {
        for (Map.Entry<String, String> entry : testCsvs.entrySet()) {
            CsvTestUtil.saveCsvFile(entry.getKey(), entry.getValue());
        }

        db = new TestGraphDatabaseFactory()
                .newImpermanentDatabaseBuilder()
                .setConfig("apoc.import.file.enabled", "true")
                .setConfig("apoc.export.file.enabled", "true")
                .setConfig("apoc.import.file.use_neo4j_config", "true")
                .setConfig("dbms.security.allow_csv_import_from_file_urls","true")
                .setConfig("dbms.directories.import",
                        new File("src/test/resources/csv-inputs").getAbsolutePath())
                .newGraphDatabase();

        TestUtil.registerProcedure(db, ImportCsv.class);
    }

    @After
    public void tearDown() {
        db.shutdown();
    }

    @Test
    public void testNodesWithIds() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {file}, labels: ['Person']}], [], {config})",
                map(
                        "file", "file:/id.csv",
                        "config", map("delimiter", '|', "stringIds", false)
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(0L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (n:Person) RETURN n.name AS name ORDER BY name");
        Assert.assertEquals("Jane", resultName.next().get("name"));
        Assert.assertEquals("John", resultName.next().get("name"));

        final Result resultId = db.execute("MATCH (n:Person) RETURN n.id AS id ORDER BY id");
        Assert.assertEquals(1L, resultId.next().get("id"));
        Assert.assertEquals(2L, resultId.next().get("id"));
    }

    @Test
    public void testCallAsString() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv(" +
                        "[{fileName: 'file:/quoted.csv', labels: ['Person']}], " +
                        "[], " +
                        "{delimiter: '|', arrayDelimiter: ':', quotationCharacter: '\\'', stringIds: false})",
                map(),
                (r) -> {
                    assertEquals(1L, r.get("nodes"));
                    assertEquals(0L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (n:Person:Student:Employee) RETURN n.name AS name ORDER BY name");
        Assert.assertEquals("John", resultName.next().get("name"));

        final Result resultId = db.execute("MATCH (n:Person:Student:Employee) RETURN n.id AS id ORDER BY id");
        Assert.assertEquals(1L, resultId.next().get("id"));
    }

    @Test
    public void testNodesWithIdSpaces() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {file}, labels: ['Person']}], [], {config})",
                map(
                        "file", "file:/id-idspaces.csv",
                        "config", map("delimiter", '|')
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(0L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (n:Person) RETURN n.name AS name ORDER BY name");
        Assert.assertEquals("Jane", resultName.next().get("name"));
        Assert.assertEquals("John", resultName.next().get("name"));
    }

    @Test
    public void testCustomLabels() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {file}, labels: ['Person']}], [], {config})",
                map(
                        "file", "file:/label.csv",
                        "config", map("delimiter", '|')
                ),
                (r) -> {
                    assertEquals(1L, r.get("nodes"));
                    assertEquals(0L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (n) UNWIND labels(n) AS label RETURN label ORDER BY label");
        Assert.assertEquals("Employee", resultName.next().get("label"));
        Assert.assertEquals("Person", resultName.next().get("label"));
        Assert.assertEquals("Student", resultName.next().get("label"));
    }

    @Test
    public void testArray() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {file}, labels: ['Person']}], [], {config})",
                map(
                        "file", "file:/array.csv",
                        "config", map("delimiter", '|')
                ),
                (r) -> {
                    assertEquals(1L, r.get("nodes"));
                    assertEquals(0L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (n:Person) UNWIND n.name AS name RETURN name ORDER BY name");
        Assert.assertEquals("Alice", resultName.next().get("name"));
        Assert.assertEquals("Bob", resultName.next().get("name"));
        Assert.assertEquals("John", resultName.next().get("name"));
    }

    @Test
    public void testDefaultTypedField() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {file}, labels: ['Person']}], [], {config})",
                map(
                        "file", "file:/typeless.csv",
                        "config", map("delimiter", '|')
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(0L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (n:Person) RETURN n.name AS name ORDER BY name");
        Assert.assertEquals("Jane", resultName.next().get("name"));
        Assert.assertEquals("John", resultName.next().get("name"));
    }

    @Test
    public void testCustomRelationshipTypes() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {nodeFile}, labels: ['Person']}], [{fileName: {relFile}, type: 'KNOWS'}], {config})",
                map(
                        "nodeFile", "file:/id.csv",
                        "relFile", "file:/rel-type.csv",
                        "config", map("delimiter", '|')
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(2L, r.get("relationships"));
                }
        );

        final Result result1 = db.execute("MATCH (p1:Person)-[:FRIENDS_WITH]->(p2:Person) RETURN p1.name + ' ' + p2.name AS pair ORDER BY pair");
        Assert.assertEquals("John Jane", result1.next().get("pair"));

        final Result result2 = db.execute("MATCH (p1:Person)-[:KNOWS]->(p2:Person) RETURN p1.name + ' ' + p2.name AS pair ORDER BY pair");
        Assert.assertEquals("Jane John", result2.next().get("pair"));

    }

    @Test
    public void testRelationshipWithoutIdSpaces() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {nodeFile}, labels: ['Person']}], [{fileName: {relFile}, type: 'KNOWS'}], {config})",
                map(
                        "nodeFile", "file:/id.csv",
                        "relFile", "file:/rel-on-ids.csv",
                        "config", map("delimiter", '|')
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(1L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (p1:Person)-[:KNOWS]->(p2:Person) RETURN p1.name + ' ' + p2.name AS pair ORDER BY pair");
        Assert.assertEquals("John Jane", resultName.next().get("pair"));
    }

    @Test
    public void testRelationshipWithIdSpaces() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {nodeFile}, labels: ['Person']}], [{fileName: {relFile}, type: 'KNOWS'}], {config})",
                map(
                        "nodeFile", "file:/id-idspaces.csv",
                        "relFile", "file:/rel-on-ids-idspaces.csv",
                        "config", map("delimiter", '|')
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(1L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (p1:Person)-[:KNOWS]->(p2:Person) RETURN p1.name + ' ' + p2.name AS pair ORDER BY pair");
        Assert.assertEquals("John Jane", resultName.next().get("pair"));
    }

    @Test
    public void testRelationshipWithCustomIdNames() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv(" +
                        "[" +
                        "  {fileName: {personFile}, labels: ['Person']}," +
                        "  {fileName: {companyFile}, labels: ['Company']}," +
                        "  {fileName: {universityFile}, labels: ['University']}" +
                        "]," +
                        "[" +
                        "  {fileName: {relFile}, type: 'AFFILIATED_WITH'}" +
                        "]," +
                        " {config})",
                map(
                        "personFile", "file:/custom-ids-basic-persons.csv",
                        "companyFile", "file:/custom-ids-basic-companies.csv",
                        "universityFile", "file:/custom-ids-basic-unis.csv",
                        "relFile", "file:/custom-ids-basic-affiliated-with.csv",
                        "config", map()
                ),
                (r) -> {
                    assertEquals(4L, r.get("nodes"));
                    assertEquals(2L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (p:Person)-[:AFFILIATED_WITH]->(org) RETURN p.name + ' ' + org.name AS pair ORDER BY pair");
        Assert.assertEquals("Jane Neo4j", resultName.next().get("pair"));
        Assert.assertEquals("John TU Munich", resultName.next().get("pair"));
    }

    @Test
    public void testRelationshipWithCustomIdNamesAndIdSpaces() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv(" +
                        "[" +
                        "  {fileName: {personFile}, labels: ['Person']}," +
                        "  {fileName: {companyFile}, labels: ['Company']}," +
                        "  {fileName: {universityFile}, labels: ['University']}" +
                        "]," +
                        "[" +
                        "  {fileName: {relFile}, type: 'AFFILIATED_WITH'}" +
                        "]," +
                        " {config})",
                map(
                        "personFile", "file:/custom-ids-idspaces-persons.csv",
                        "companyFile", "file:/custom-ids-idspaces-companies.csv",
                        "universityFile", "file:/custom-ids-idspaces-unis.csv",
                        "relFile", "file:/custom-ids-idspaces-affiliated-with.csv",
                        "config", map()
                ),
                (r) -> {
                    assertEquals(4L, r.get("nodes"));
                    assertEquals(2L, r.get("relationships"));
                }
        );

        final Result resultName = db.execute("MATCH (p:Person)-[:AFFILIATED_WITH]->(org) RETURN p.name + ' ' + org.name AS pair ORDER BY pair");
        Assert.assertEquals("Jane Neo4j", resultName.next().get("pair"));
        Assert.assertEquals("John TU Munich", resultName.next().get("pair"));
    }

    @Test
    public void ignoreFieldType() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {nodeFile}, labels: ['Person']}], [{fileName: {relFile}, type: 'KNOWS'}], {config})",
                map(
                        "nodeFile", "file:/ignore-nodes.csv",
                        "relFile", "file:/ignore-relationships.csv",
                        "config", map("delimiter", '|', "batchSize", 1)
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(2L, r.get("relationships"));
                }
        );

        final Result result1 = db.execute(
                "MATCH (p:Person)\n" +
                        "RETURN p.age AS age ORDER BY age"
        );
        Assert.assertEquals(25L, result1.next().get("age"));
        Assert.assertEquals(26L, result1.next().get("age"));

        final Result result2 = db.execute(
                "MATCH (p1:Person)-[k:KNOWS]->(p2:Person)\n" +
                        "WHERE p1.lastname IS NULL\n" +
                        "  AND p2.lastname IS NULL\n" +
                        "  AND k.prop1 IS NULL\n" +
                        "RETURN p1.firstname + ' ' + p1.age + ' <' + k.prop2 + '> ' + p2.firstname + ' ' + p2.age AS pair ORDER BY pair"
            );
        Assert.assertEquals("Jane 26 <6> John 25", result2.next().get("pair"));
        Assert.assertEquals("John 25 <3> Jane 26", result2.next().get("pair"));
    }

    @Test
    public void testNoDuplicationsCreated() {
        TestUtil.testCall(
                db,
                "CALL apoc.import.csv([{fileName: {nodeFile}, labels: ['Person']}], [{fileName: {relFile}, type: 'KNOWS'}], {config})",
                map(
                        "nodeFile", "file:/persons.csv",
                        "relFile", "file:/knows.csv",
                        "config", map("stringIds", false)
                ),
                (r) -> {
                    assertEquals(2L, r.get("nodes"));
                    assertEquals(1L, r.get("relationships"));
                }
        );
    }

}
