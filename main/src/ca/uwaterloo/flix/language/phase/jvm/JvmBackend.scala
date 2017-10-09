/*
 * Copyright 2017 Magnus Madsen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ca.uwaterloo.flix.language.phase.jvm

import java.nio.file.{Path, Paths}

import ca.uwaterloo.flix.api.Flix
import ca.uwaterloo.flix.language.CompilationError
import ca.uwaterloo.flix.language.ast.ExecutableAst._
import ca.uwaterloo.flix.language.phase.Phase
import ca.uwaterloo.flix.util.Validation
import ca.uwaterloo.flix.util.Validation._

object JvmBackend extends Phase[Root, Root] {

  /**
    * The directory where to place the generated class files.
    */
  val TargetDirectory: Path = Paths.get("./target/flix/")

  /**
    * Emits JVM bytecode for the given AST `root`.
    */
  def run(root: Root)(implicit flix: Flix): Validation[Root, CompilationError] = {
    // return root.toSuccess
    //
    // Put the AST root into implicit scope.
    //
    implicit val _ = root

    //
    // Compute the set of namespaces in the program.
    //
    val namespaces = JvmOps.namespacesOf(root)

    //
    // Compute the set of types in the program.
    //
    val types = JvmOps.typesOf(root)

    //
    // Compute the set of instantiated tags in the program.
    //
    val tags = JvmOps.tagsOf(root)

    //
    // Generate the Context class.
    //
    val contextClass = GenContext.gen(namespaces)

    //
    // Generate the namespace classes.
    //
    val namespaceClasses = GenNamespaces.gen(namespaces)

    //
    // Generate continuation interfaces for each function type in the program.
    //
    val continuationInterfaces = GenContinuationInterfaces.gen(types)

    //
    // Generate function interfaces for each function type in the program.
    //
    val functionInterfaces = GenFunctionInterfaces.gen(types)

    //
    // Generate function classes for each function in the program.
    //
    val functionClasses = GenFunctionClasses.gen(root.defs)

    //
    // Generate enum interfaces for each enum type in the program.
    //
    val enumInterfaces = GenEnumInterfaces.gen(types)

    //
    // Generate tag classes for each enum instantiation in the program.
    //
    val tagClasses = GenTagClasses.gen(tags)

    //
    // Generate tuple interfaces for each tuple type in the program.
    //
    val tupleInterfaces = GenTupleInterfaces.gen(types)

    //
    // Generate tuple classes for each tuple type in the program.
    //
    val tupleClasses = GenTupleClasses.gen(types)

    //
    // Generate tag-tuple fusion classes for tag-tuple in the program.
    //
    val fusionClasses = GenFusionClasses.gen()

    //
    // Generate the main class.
    //
    val mainClass = GenMain.gen()

    //
    // Collect all the classes and interfaces together.
    //
    // TODO: Re-order
    val allClasses = contextClass ++ namespaceClasses ++ continuationInterfaces ++ functionInterfaces ++
      functionClasses ++ enumInterfaces ++ tupleInterfaces ++ tupleClasses ++ tagClasses ++ mainClass ++
      fusionClasses

    //
    // Write each class (and interface) to disk.
    //
    // NB: In test mode we skip writing the files to disk.
    if (!flix.options.test) {
      for ((name, clazz) <- allClasses) {
        JvmOps.writeClass(TargetDirectory, clazz)
      }
    }

    root.toSuccess
  }

}
