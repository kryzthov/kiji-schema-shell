/**
 * (c) Copyright 2012 WibiData, Inc.
 *
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kiji.schema.shell.ddl

import scala.collection.JavaConversions._
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.specs2.mutable._

import org.kiji.schema.KConstants
import org.kiji.schema.KijiURI
import org.kiji.schema.avro.FamilyDesc
import org.kiji.schema.layout.KijiTableLayout

import org.kiji.schema.shell.DDLException
import org.kiji.schema.shell.DDLParser
import org.kiji.schema.shell.Environment
import org.kiji.schema.shell.InputProcessor
import org.kiji.schema.shell.input.StringInputSource

class TestTableOperations extends CommandTestCase {
  "Various table operations" should {
    "create a table" in {
      val parser = getParser()
      val res = parser.parseAll(parser.statement, """
CREATE TABLE foo WITH DESCRIPTION 'some data'
ROW KEY FORMAT HASHED
WITH LOCALITY GROUP default WITH DESCRIPTION 'main storage' (
  MAXVERSIONS = INFINITY,
  TTL = FOREVER,
  INMEMORY = false,
  COMPRESSED WITH GZIP,
  FAMILY info WITH DESCRIPTION 'basic information' (
    name "string" WITH DESCRIPTION 'The user\'s name',
    email "string",
    age "int"),
  MAP TYPE FAMILY integers COUNTER WITH DESCRIPTION 'metric tracking data'
);""");
      res.successful mustEqual true
      res.get must beAnInstanceOf[CreateTableCommand]
      val env2 = res.get.exec()

      // Check that we have created as many locgroups, map families, and group families
      // as we expect to be here.
      val maybeLayout2 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout2 must beSome[KijiTableLayout]
      val layout2 = maybeLayout2.get.getDesc
      val locGroups2 = layout2.getLocalityGroups()
      locGroups2.size mustEqual 1
      val defaultLocGroup2 = locGroups2.head
      defaultLocGroup2.getName().toString() mustEqual "default"
      defaultLocGroup2.getFamilies().size mustEqual 2
      (defaultLocGroup2.getFamilies().filter({ grp => grp.getName().toString() == "integers" })
          .size mustEqual 1)
      val maybeInfo = defaultLocGroup2.getFamilies().find({ grp =>
          grp.getName().toString() == "info" })
      maybeInfo must beSome[FamilyDesc]
      maybeInfo.get.getColumns().size mustEqual 3
    }

    "create a table and add a column" in {
      val parser = getParser()
      val res = parser.parseAll(parser.statement, """
CREATE TABLE foo WITH DESCRIPTION 'some data'
ROW KEY FORMAT HASHED
WITH LOCALITY GROUP default WITH DESCRIPTION 'main storage' (
  MAXVERSIONS = INFINITY,
  TTL = FOREVER,
  INMEMORY = false,
  COMPRESSED WITH GZIP,
  FAMILY info WITH DESCRIPTION 'basic information' (
    name "string" WITH DESCRIPTION 'The user\'s name',
    email "string",
    age "int")
);""");
      res.successful mustEqual true
      res.get must beAnInstanceOf[CreateTableCommand]
      val env2 = res.get.exec()

      // Check that we have created as many locgroups, map families, and group families
      // as we expect to be here.
      val maybeLayout2 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout2 must beSome[KijiTableLayout]
      val layout2 = maybeLayout2.get.getDesc
      val locGroups2 = layout2.getLocalityGroups()
      locGroups2.size mustEqual 1
      val defaultLocGroup2 = locGroups2.head
      defaultLocGroup2.getName().toString() mustEqual "default"
      defaultLocGroup2.getFamilies().size mustEqual 1
      defaultLocGroup2.getFamilies().head.getColumns().size mustEqual 3

      // Add a column.
      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo ADD COLUMN info:meep "string" WITH DESCRIPTION 'beep beep!';""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableAddColumnCommand]
      val env3 = res2.get.exec()

      // Check that the new column is here.
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      locGroups3.size mustEqual 1
      val defaultLocGroup3 = locGroups3.head
      defaultLocGroup3.getFamilies().size mustEqual 1
      defaultLocGroup3.getFamilies().head.getColumns().size mustEqual 4
      var meepExists = false
      defaultLocGroup3.getFamilies().head.getColumns().foreach { col =>
        if (col.getName().toString().equals("meep")) {
          meepExists = true
          col.getColumnSchema().getValue().toString() mustEqual "\"string\""
        }
      }
      meepExists mustEqual true
    }

    "create a table and rename a column" in {
      val env2 = createBasicTable()

      // Add a column.
      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo RENAME COLUMN info:email AS info:mail;""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableRenameColumnCommand]
      val env3 = res2.get.exec()

      // Check that the new column is here.
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      locGroups3.size mustEqual 1
      val defaultLocGroup3 = locGroups3.head
      defaultLocGroup3.getFamilies().size mustEqual 1
      var mailExists = false
      var emailExists = false
      defaultLocGroup3.getFamilies().head.getColumns().foreach { col =>
        if (col.getName().toString().equals("mail")) {
          mailExists = true
          col.getColumnSchema().getValue().toString() mustEqual "\"string\""
        } else if (col.getName().toString().equals("email")) {
          emailExists = true
        }
      }
      mailExists mustEqual true
      emailExists mustEqual false
    }

    "create a table and drop a column" in {
      val env2 = createBasicTable()

      // Drop a column.
      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo DROP COLUMN info:email;""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableDropColumnCommand]
      val env3 = res2.get.exec()

      // Check that the new column is here.
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      val defaultLocGroup3 = locGroups3.head
      var emailExists = false
      defaultLocGroup3.getFamilies().head.getColumns().foreach { col =>
        if (col.getName().toString().equals("email")) {
          emailExists = true
        }
      }
      emailExists mustEqual false
    }

    "verify inputProcessor returns the right environment" in {
      val env2 = createBasicTable()

      val env3 = env2.withInputSource(new StringInputSource("""CREATE TABLE t
  WITH DESCRIPTION 'quit' WITH LOCALITY GROUP lg;"""))
      val inputProcessor = new InputProcessor
      val buf = new StringBuilder
      val resEnv = inputProcessor.processUserInput(buf, env3)
      resEnv.containsTable("t") mustEqual true

    }

    "parse statements with embedded quit line" in {
      val env2 = createBasicTable()

      val env3 = env2.withInputSource(new StringInputSource("""CREATE TABLE t
  WITH DESCRIPTION '
quit
' WITH LOCALITY GROUP lg;"""))
      val inputProcessor = new InputProcessor
      val buf = new StringBuilder
      val resEnv = inputProcessor.processUserInput(buf, env3)
      resEnv.containsTable("t") mustEqual true
    }

    "parse statements over many lines" in {
      val env2 = createBasicTable()

      val env3 = env2.withInputSource(new StringInputSource("""CREATE
TABLE
t
WITH
DESCRIPTION
'd'
WITH
LOCALITY GROUP lg;"""))
      val inputProcessor = new InputProcessor
      val buf = new StringBuilder
      val resEnv = inputProcessor.processUserInput(buf, env3)
      resEnv.containsTable("t") mustEqual true
    }

    "create a table and drop a locality group" in {
      val env2 = createBasicTable()

      // Drop a locality group.
      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo DROP LOCALITY GROUP default;""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableDropLocalityGroupCommand]
      val env3 = res2.get.exec()

      // Check that we have 0 locality groups.
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      locGroups3.size mustEqual 0
    }

    "create a table and rename a locality group" in {
      val env2 = createBasicTable()

      // Rename locality group.
      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo RENAME LOCALITY GROUP default def;""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableRenameLocalityGroupCommand]
      val env3 = res2.get.exec()

      // Check that the locality group has the new name
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      locGroups3.size mustEqual 1
      locGroups3.head.getName().toString() mustEqual "def"
    }

    "create a table and update a family description" in {
      val env2 = createBasicTable()

      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo SET DESCRIPTION = 'testing!' FOR FAMILY info;""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableDescForFamilyCommand]
      val env3 = res2.get.exec()

      // Check that the family has a new description.
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      (layout3.getLocalityGroups().head.getFamilies().find({ fam =>
        fam.getName().toString == "info" }).get.getDescription().toString
        mustEqual "testing!")
    }

    "create a table and update the table description" in {
      val env2 = createBasicTable()

      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo SET DESCRIPTION = 'testing!';""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableDescCommand]
      val env3 = res2.get.exec()

      // Check that the table has an updated description.
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      maybeLayout3.get.getDesc().getDescription().toString() mustEqual "testing!"
    }

    "create a table and update the locality group description" in {
      val env2 = createBasicTable()

      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo SET DESCRIPTION = 'testing!' FOR LOCALITY GROUP 'default';""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterTableDescForLocalityGroupCommand]
      val env3 = res2.get.exec()

      // Check that the locality group has the new description
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      locGroups3.size mustEqual 1
      locGroups3.head.getName().toString() mustEqual "default"
      locGroups3.head.getDescription().toString() mustEqual "testing!"
    }

    "create a table and update a locality group property" in {
      val env2 = createBasicTable()

      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo SET INMEMORY = true FOR LOCALITY GROUP 'default';""")
      res2.successful mustEqual true
      res2.get must beAnInstanceOf[AlterLocalityGroupPropertyCommand]
      val env3 = res2.get.exec()

      // Check that the locality group has the new property
      val maybeLayout3 = env3.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      locGroups3.size mustEqual 1
      locGroups3.head.getName().toString() mustEqual "default"
      locGroups3.head.getInMemory() mustEqual true
    }

    "alter a map type family schema" in {
      val env2 = createBasicTable()

      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo ADD MAP TYPE FAMILY ints "int" TO LOCALITY GROUP default;""")
      res2.successful mustEqual true
      val env3 = res2.get.exec()

      // Check that the new family exists.
      val maybeLayout3 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc
      val locGroups3 = layout3.getLocalityGroups()
      locGroups3.size mustEqual 1
      locGroups3.head.getFamilies().size mustEqual 2
      val maybeMapFamily = locGroups3.head.getFamilies().find({ fam =>
        fam.getName().toString == "ints"})
      maybeMapFamily must beSome[FamilyDesc]
      val mapFamily = maybeMapFamily.get
      mapFamily.getName().toString mustEqual "ints"
      mapFamily.getMapSchema().getValue().toString() mustEqual "\"int\""

      // Set the new family's schema to "string".
      val parser3 = new DDLParser(env3)
      val res3 = parser3.parseAll(parser3.statement, """
ALTER TABLE foo SET SCHEMA = "string" FOR MAP TYPE FAMILY ints;""")
      res3.successful mustEqual true
      val env4 = res3.get.exec()
      val maybeLayout4 = env.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout4 must beSome[KijiTableLayout]
      val layout4 = maybeLayout4.get.getDesc
      val locGroups4 = layout4.getLocalityGroups()
      val maybeMapFamily2 = locGroups4.head.getFamilies().find({ fam =>
        fam.getName().toString == "ints"})
      maybeMapFamily2 must beSome[FamilyDesc]
      val mapFamily2 = maybeMapFamily2.get

      mapFamily2.getName().toString mustEqual "ints"
      mapFamily2.getMapSchema().getValue().toString() mustEqual "\"string\""
    }

    "alter a column schema" in {
      val env2 = createBasicTable()

      val parser2 = new DDLParser(env2)
      val res2 = parser2.parseAll(parser2.statement, """
ALTER TABLE foo SET SCHEMA = "int" FOR COLUMN info:email;""")
      res2.successful mustEqual true
      val env3 = res2.get.exec()

      // Check that the new family exists.
      val maybeLayout2 = env3.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout2 must beSome[KijiTableLayout]
      val layout2 = maybeLayout2.get.getDesc
      val locGroups2 = layout2.getLocalityGroups()
      locGroups2.size mustEqual 1
      locGroups2.head.getFamilies().head.getName().toString() mustEqual "info"
      locGroups2.head.getFamilies().head.getColumns().foreach { col =>
        if (col.getName().toString().equals("email")) {
          col.getColumnSchema().getValue().toString() mustEqual "\"int\""
        }
      }
    }

    "dump table ddl correctly" in {
      val env2 = createBasicTable()
      val baos = new ByteArrayOutputStream()
      val printStream = new PrintStream(baos)
      val parser = new DDLParser(env2.withPrinter(printStream))
      val res2 = parser.parseAll(parser.statement, "DUMP DDL FOR TABLE foo;")
      res2.successful mustEqual true
      val env3 = res2.get.exec()

      // Retrieve the output DDL string from the environment.
      printStream.close()
      val generatedDdl = baos.toString()
      System.out.println("Got output ddl: [" + generatedDdl + "]")

      // Also record the current table layout.
      val maybeLayout = env3.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout must beSome[KijiTableLayout]
      val layout = maybeLayout.get.getDesc

      // Drop the table.
      val parser2 = new DDLParser(env3.withPrinter(System.out))
      val res3 = parser2.parseAll(parser2.statement, "DROP TABLE foo;")
      res3.successful mustEqual true
      val env4 = res3.get.exec()

      // Verify that the table is dropped.
      val maybeLayout2 = env4.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout2 must beNone

      // Recreate it using the auto-generated DDL.
      val parser3 = new DDLParser(env4)
      val res4 = parser3.parseAll(parser3.statement, generatedDdl)
      res4.successful mustEqual true
      val env5 = res4.get.exec()

      val maybeLayout3 = env5.kijiSystem.getTableLayout(
          defaultURI, "foo")
      maybeLayout3 must beSome[KijiTableLayout]
      val layout3 = maybeLayout3.get.getDesc

      // Check that the table layout is exactly the same as the original layout.
      layout mustEqual layout3
    }
  }

  def getParser(): DDLParser = { new DDLParser(env) }

  /**
   * Create a table and return the Environment object where it exists.
   */
  def createBasicTable(): Environment = {
    val parser = getParser()
    val res = parser.parseAll(parser.statement, """
CREATE TABLE foo WITH DESCRIPTION 'some data'
ROW KEY FORMAT HASHED
WITH LOCALITY GROUP default WITH DESCRIPTION 'main storage' (
  MAXVERSIONS = INFINITY,
  TTL = FOREVER,
  INMEMORY = false,
  COMPRESSED WITH GZIP,
  FAMILY info WITH DESCRIPTION 'basic information' (
    name "string" WITH DESCRIPTION 'The user\'s name',
    email "string",
    age "int")
);""");
    return res.get.exec()
  }
}
