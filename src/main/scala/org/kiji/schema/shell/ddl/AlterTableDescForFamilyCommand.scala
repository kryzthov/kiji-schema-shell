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

import org.kiji.schema.avro.TableLayoutDesc
import org.kiji.schema.layout.KijiTableLayout

import org.kiji.schema.shell.DDLException
import org.kiji.schema.shell.Environment

class AlterTableDescForFamilyCommand(
    val env: Environment,
    val tableName: String,
    val familyName: String,
    val description: String) extends TableDDLCommand {

  override def validateArguments(): Unit = {
    val layout = getInitialLayout()
    checkColFamilyExists(layout, familyName)
  }

  override def updateLayout(layout: TableLayoutDesc.Builder): Unit = {
    getFamily(layout, familyName).getOrElse(throw new DDLException("Missing!"))
        .setDescription(description)
  }
}
