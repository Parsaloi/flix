package ca.uwaterloo.flix.language.phase

import ca.uwaterloo.flix.language._
import ca.uwaterloo.flix.language.ast._
import ca.uwaterloo.flix.language.ast.ExecutableAst.Expression
import ca.uwaterloo.flix.language.ast.ExecutableAst.Expression._
import ca.uwaterloo.flix.runtime.verifier._
import ca.uwaterloo.flix.util._
import ca.uwaterloo.flix.util.Validation._
import com.microsoft.z3._

object Verifier {

  /**
    * A common super-type for verification errors.
    */
  sealed trait VerifierError extends CompilationError

  object VerifierError {

    implicit val consoleCtx = Compiler.ConsoleCtx

    /**
      * An error raised to indicate that a function is not associative.
      */
    case class AssociativityError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The function is not associative.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The function was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that a function is not commutative.
      */
    case class CommutativityError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The function is not commutative.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The function was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that a partial order is not reflexive.
      */
    case class ReflexivityError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The partial order is not reflexive.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The partial order was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that a partial order is not anti-symmetric.
      */
    case class AntiSymmetryError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The partial order is not anti-symmetric.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The partial order was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that a partial order is not transitive.
      */
    case class TransitivityError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The partial order is not transitive.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The partial order was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the least element is not smallest.
      */
    case class LeastElementError(loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The least element is not the smallest.")}
           |
           |The partial order was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the lub is not an upper bound.
      */
    case class UpperBoundError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The lub is not an upper bound.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The lub was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the lub is not a least upper bound.
      */
    case class LeastUpperBoundError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The lub is not a least upper bound.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The lub was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the greatest element is not the largest.
      */
    case class GreatestElementError(loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The greatest element is not the largest.")}
           |
           |The partial order was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the glb is not a lower bound.
      */
    case class LowerBoundError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The glb is not a lower bound.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The glb was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the glb is not the greatest lower bound.
      */
    case class GreatestLowerBoundError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The glb is not a greatest lower bound.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The glb was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the function is not strict.
      */
    case class StrictError(loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The function is not strict.")}
           |
           |The function was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the function is not monotone.
      */
    case class MonotoneError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The function is not monotone.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The function was defined here:
           |${loc.underline}
           """.stripMargin
    }


    /**
      * An error raised to indicate that the height function may be negative.
      */
    case class HeightNonNegativeError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The height function is not non-negative.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The height function was defined here:
           |${loc.underline}
           """.stripMargin
    }

    /**
      * An error raised to indicate that the height function is not strictly decreasing.
      */
    case class HeightStrictlyDecreasingError(m: Map[String, String], loc: SourceLocation) extends VerifierError {
      val message =
        s"""${consoleCtx.blue(s"-- VERIFIER ERROR -------------------------------------------------- ${loc.source.format}")}
           |
           |${consoleCtx.red(s">> The height function is not strictly decreasing.")}
           |
           |Counter-example: ${m.mkString(", ")}
           |
           |The height function was defined here:
           |${loc.underline}
           """.stripMargin
    }

  }

  /**
    * Attempts to verify all properties in the given AST.
    */
  def verify(root: ExecutableAst.Root, options: Options)(implicit genSym: GenSym): Validation[ExecutableAst.Root, VerifierError] = {
    /*
     * Check if verification is enabled. Otherwise return success immediately.
     */
    if (options.verify != Verify.Enabled) {
      return root.toSuccess
    }

    /*
     * Verify each property.
     */
    val results = root.properties.map(p => verifyProperty(p, root))

    /*
     * Print verbose information (if enabled).
     */
    if (options.verbosity == Verbosity.Verbose) {
      printVerbose(results)
    }

    /*
     * Returns the original AST root if all properties verified successfully.
     */
    if (isSuccess(results)) {
      val time = root.time.copy(verifier = totalElapsed(results))
      root.copy(time = time).toSuccess
    } else {
      val errors = results.collect {
        case PropertyResult.Failure(_, _, _, _, error) => error
      }
      Validation.Failure(errors.toVector)
    }
  }

  /**
    * Attempts to verify the given `property`.
    *
    * Returns `None` if the property is satisfied.
    * Otherwise returns `Some` containing the verification error.
    */
  def verifyProperty(property: ExecutableAst.Property, root: ExecutableAst.Root)(implicit genSym: GenSym): PropertyResult = {
    // begin time measurement.
    val t = System.nanoTime()

    // the base expression.
    val exp0 = property.exp

    // a sequence of environments under which the base expression must hold.
    val envs = enumerate(getUniversallyQuantifiedVariables(exp0))

    // the number of paths explored by the symbolic evaluator.
    var paths = 0

    // the number of queries issued to the SMT solver.
    var queries = 0

    // attempt to verify that the property holds under each environment.
    val violations = envs flatMap {
      case env0 =>
        SymbolicEvaluator.eval(peelUniversallyQuantifiers(exp0), env0, root) flatMap {
          case (Nil, SymVal.True) =>
            // Case 1: The symbolic evaluator proved the property.
            paths += 1
            Nil
          case (Nil, SymVal.False) =>
            // Case 2: The symbolic evaluator disproved the property.
            val env1 = env0.foldLeft(Map.empty[String, String]) {
              case (macc, (k, e)) => macc + (k -> e.toString)
            }
            paths += 1
            List(toVerifierError(property, env1))
          case (pc, v) => v match {
            case SymVal.True =>
              // Case 3.1: The property holds under some path condition.
              // The property holds regardless of whether the path condition is satisfiable.
              paths += 1
              Nil
            case SymVal.False =>
              // Case 3.2: The property *does not* hold under some path condition.
              // If the path condition is satisfiable then the property *does not* hold.
              paths += 1
              queries += 1
              SmtSolver.mkContext(ctx => {
                val q = visitPathConstraint(pc, ctx)
                SmtSolver.checkSat(q, ctx) match {
                  case SmtResult.Unsatisfiable =>
                    // Case 3.1: The formula is UNSAT, i.e. the property HOLDS.
                    Nil
                  case SmtResult.Satisfiable(model) =>
                    // Case 3.2: The formula is SAT, i.e. a counter-example to the property exists.
                    List(toVerifierError(property, mkModel(env0, model)))
                  case SmtResult.Unknown =>
                    // Case 3.3: It is unknown whether the formula has a model.
                    ???
                }
              })
          }
        }

    }

    // end time measurement.
    val e = System.nanoTime() - t

    if (violations.isEmpty) {
      PropertyResult.Success(property, paths, queries, e)
    } else {
      PropertyResult.Failure(property, paths, queries, e, violations.head)
    }

  }

  /**
    * Returns a list of all the universally quantified variables in the given expression `exp0`.
    */
  def getUniversallyQuantifiedVariables(exp0: Expression): List[Var] = exp0 match {
    case Expression.Universal(params, _, _) => params.map {
      case Ast.FormalParam(ident, tpe) => Var(ident, -1, tpe, SourceLocation.Unknown)
    }
    case _ => Nil
  }

  /**
    * Returns the expression `exp0` with all universal quantifiers stripped.
    */
  def peelUniversallyQuantifiers(exp0: Expression): Expression = exp0 match {
    case Expression.Existential(params, exp, loc) => peelUniversallyQuantifiers(exp)
    case Expression.Universal(params, exp, loc) => peelUniversallyQuantifiers(exp)
    case _ => exp0
  }

  /**
    * Enumerates all possible environments of the given universally quantified variables.
    */
  // TODO: replace string by name?
  // TODO: Cleanup
  // TODO: Return SymVal.
  def enumerate(q: List[Var])(implicit genSym: GenSym): List[Map[String, SymVal]] = {
    // Unqualified formula. Used the empty environment.
    if (q.isEmpty)
      return List(Map.empty)

    def visit(tpe: Type): List[SymVal] = tpe match {
      case Type.Unit => List(SymVal.Unit)
      case Type.Bool => List(SymVal.True, SymVal.False)
      case Type.Char => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Float32 => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Float64 => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Int8 => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Int16 => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Int32 => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Int64 => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Str => List(SymVal.AtomicVar(genSym.fresh2()))
      case Type.Enum(name, cases) =>
        val r = cases flatMap {
          case (tag, tagType) =>
            visit(tagType.tpe) map {
              case e => SymVal.Tag(tag, e)
            }
        }
        r.toList
      case Type.Tuple(elms) => ???
    }

    def expand(rs: List[(String, List[SymVal])]): List[Map[String, SymVal]] = rs match {
      case Nil => List(Map.empty)
      case (quantifier, expressions) :: xs => expressions flatMap {
        case expression => expand(xs) map {
          case m => m + (quantifier -> expression)
        }
      }
    }

    val result = q map {
      case quantifier => quantifier.ident.name -> visit(quantifier.tpe)
    }
    expand(result)
  }

  /**
    * Returns a stringified model of `env` where all free variables have been
    * replaced by their corresponding values from the Z3 model `model`.
    */
  private def mkModel(env: Map[String, SymVal], model: Model): Map[String, String] = {
    def visit(e0: SymVal): String = e0 match {
      case SymVal.AtomicVar(id) => getConstant(id, model)
      case SymVal.Unit => "#U"
      case SymVal.True => "true"
      case SymVal.False => "false"
      case SymVal.Char(c) => c.toString
      case SymVal.Float32(f) => f.toString
      case SymVal.Float64(f) => f.toString
      case SymVal.Int8(i) => i.toString
      case SymVal.Int16(i) => i.toString
      case SymVal.Int32(i) => i.toString
      case SymVal.Int64(i) => i.toString
      case SymVal.Str(s) => s
      case SymVal.Tag(tag, SymVal.Unit) => tag
      case SymVal.Tag(tag, elm) => tag + "(" + visit(elm) + ")"
      case SymVal.Tuple(elms) => "(" + elms.map(visit).mkString(", ") + ")"
      case SymVal.Closure(_, _, _) => "<<closure>>"
      case SymVal.Environment(_) => "<<environment>>"
      case SymVal.UserError(loc) => "UserError(" + loc.format + ")"
      case SymVal.MatchError(loc) => "MatchError(" + loc.format + ")"
      case SymVal.SwitchError(loc) => "SwitchError(" + loc.format + ")"
    }

    env.foldLeft(Map.empty[String, String]) {
      case (macc, (key, value)) => macc + (key -> visit(value))
    }
  }

  /**
    * Returns a string representation of the given constant `id` in the Z3 model `m`.
    */
  private def getConstant(id: Name.Ident, m: Model): String = {
    for (decl <- m.getConstDecls) {
      if (id.name == decl.getName.toString) {
        return m.getConstInterp(decl).toString // TODO: Improve formatting.
      }
    }
    "???"
  }

  /**
    * Returns a verifier error for the given property `prop` under the given environment `env`.
    */
  private def toVerifierError(prop: ExecutableAst.Property, env: Map[String, String]): VerifierError = prop.law match {
    case Law.Associativity => VerifierError.AssociativityError(env, prop.loc)
    case Law.Commutativity => VerifierError.CommutativityError(env, prop.loc)
    case Law.Reflexivity => VerifierError.ReflexivityError(env, prop.loc)
    case Law.AntiSymmetry => VerifierError.AntiSymmetryError(env, prop.loc)
    case Law.Transitivity => VerifierError.TransitivityError(env, prop.loc)
    case Law.LeastElement => VerifierError.LeastElementError(prop.loc)
    case Law.UpperBound => VerifierError.UpperBoundError(env, prop.loc)
    case Law.LeastUpperBound => VerifierError.LeastUpperBoundError(env, prop.loc)
    case Law.GreatestElement => VerifierError.GreatestElementError(prop.loc)
    case Law.LowerBound => VerifierError.LowerBoundError(env, prop.loc)
    case Law.GreatestLowerBound => VerifierError.GreatestLowerBoundError(env, prop.loc)
    case Law.Strict => VerifierError.StrictError(prop.loc)
    case Law.Monotone => VerifierError.MonotoneError(env, prop.loc)
    case Law.HeightNonNegative => VerifierError.HeightNonNegativeError(env, prop.loc)
    case Law.HeightStrictlyDecreasing => VerifierError.HeightStrictlyDecreasingError(env, prop.loc)
  }

  /**
    * Translates the given path constraint `pc` into a boolean Z3 expression.
    */
  private def visitPathConstraint(pc: List[SmtExpr], ctx: Context): BoolExpr = pc.foldLeft(ctx.mkBool(true)) {
    case (f, e) => ctx.mkAnd(f, visitBoolExpr(e, ctx))
  }

  /**
    * Translates the given SMT expression `exp0` into a Z3 boolean expression.
    */
  private def visitBoolExpr(exp0: SmtExpr, ctx: Context): BoolExpr = exp0 match {
    case SmtExpr.Var(id, tpe) => ctx.mkBoolConst(id.name)
    case SmtExpr.Not(e) => ctx.mkNot(visitBoolExpr(e, ctx))
    case SmtExpr.LogicalAnd(e1, e2) => ctx.mkAnd(visitBoolExpr(e1, ctx), visitBoolExpr(e2, ctx))
    case SmtExpr.LogicalOr(e1, e2) => ctx.mkOr(visitBoolExpr(e1, ctx), visitBoolExpr(e2, ctx))
    case SmtExpr.Implication(e1, e2) => ctx.mkImplies(visitBoolExpr(e1, ctx), visitBoolExpr(e2, ctx))
    case SmtExpr.Bicondition(e1, e2) => ctx.mkIff(visitBoolExpr(e1, ctx), visitBoolExpr(e2, ctx))
    case SmtExpr.Less(e1, e2) => ctx.mkBVSLT(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.LessEqual(e1, e2) => ctx.mkBVSLE(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.Greater(e1, e2) => ctx.mkBVSGT(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.GreaterEqual(e1, e2) => ctx.mkBVSGE(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.Equal(e1, e2) => e1.tpe match {
      case Type.Bool => ctx.mkIff(visitBoolExpr(e1, ctx), visitBoolExpr(e2, ctx))
      case _ => ctx.mkEq(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    }
    case SmtExpr.NotEqual(e1, e2) => e1.tpe match {
      case Type.Bool => ctx.mkXor(visitBoolExpr(e1, ctx), visitBoolExpr(e2, ctx))
      case _ => ctx.mkNot(ctx.mkEq(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx)))
    }
  }

  /**
    * Translates the given SMT expression `exp0` into a Z3 bit vector expression.
    */
  private def visitBitVecExpr(exp0: SmtExpr, ctx: Context): BitVecExpr = exp0 match {
    case SmtExpr.Int8(i) => ctx.mkBV(i, 8)
    case SmtExpr.Int16(i) => ctx.mkBV(i, 16)
    case SmtExpr.Int32(i) => ctx.mkBV(i, 32)
    case SmtExpr.Int64(i) => ctx.mkBV(i, 64)
    case SmtExpr.Var(id, tpe) => tpe match {
      case Type.Int8 => ctx.mkBVConst(id.name, 8)
      case Type.Int16 => ctx.mkBVConst(id.name, 16)
      case Type.Int32 => ctx.mkBVConst(id.name, 32)
      case Type.Int64 => ctx.mkBVConst(id.name, 64)
      case _ => throw InternalCompilerException(s"Unexpected non-int type: '$tpe'.")
    }
    case SmtExpr.Plus(e1, e2) => ctx.mkBVAdd(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.Minus(e1, e2) => ctx.mkBVSub(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.Times(e1, e2) => ctx.mkBVMul(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.Divide(e1, e2) => ctx.mkBVSDiv(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.Modulo(e1, e2) => ctx.mkBVSMod(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.Exponentiate(e1, e2) => ??? // TODO
    case SmtExpr.BitwiseNegate(e) => ctx.mkBVNeg(visitBitVecExpr(e, ctx))
    case SmtExpr.BitwiseAnd(e1, e2) => ctx.mkBVAND(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.BitwiseOr(e1, e2) => ctx.mkBVOR(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.BitwiseXor(e1, e2) => ctx.mkBVXOR(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.BitwiseLeftShift(e1, e2) => ctx.mkBVSHL(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case SmtExpr.BitwiseRightShift(e1, e2) => ctx.mkBVLSHR(visitBitVecExpr(e1, ctx), visitBitVecExpr(e2, ctx))
    case _ => throw InternalCompilerException(s"Unexpected SMT expression: '$exp0'.")
  }

  /**
    * Returns `true` if all the given property results `rs` are successful
    */
  private def isSuccess(rs: List[PropertyResult]): Boolean = rs.forall {
    case p: PropertyResult.Success => true
    case p: PropertyResult.Failure => false
    case p: PropertyResult.Unknown => false
  }

  /**
    * Returns the number of successes of the given property results `rs`.
    */
  private def numberOfSuccess(rs: List[PropertyResult]): Int = rs.count {
    case p: PropertyResult.Success => true
    case p: PropertyResult.Failure => false
    case p: PropertyResult.Unknown => false
  }

  /**
    * Returns the total number of paths of the given property results `rs`.
    */
  private def totalPaths(rs: List[PropertyResult]): Int = rs.foldLeft(0) {
    case (acc, res) => acc + res.paths
  }

  /**
    * Returns the total number of queries of the given property results `rs`.
    **/
  private def totalQueries(rs: List[PropertyResult]): Int = rs.foldLeft(0) {
    case (acc, res) => acc + res.queries
  }

  /**
    * Returns the total elapsed time of the property results `rs`.
    */
  private def totalElapsed(rs: List[PropertyResult]): Long = rs.foldLeft(0L) {
    case (acc, res) => acc + res.elapsed
  }

  /**
    * Prints verbose results.
    */
  def printVerbose(results: List[PropertyResult]): Unit = {
    implicit val consoleCtx = Compiler.ConsoleCtx
    Console.println(consoleCtx.blue(s"-- VERIFIER RESULTS --------------------------------------------------"))

    for (result <- results) {
      result match {
        case PropertyResult.Success(property, paths, queries, elapsed) =>
          Console.println(consoleCtx.cyan("✓ ") + property.law + " (" + property.loc.format + ")" + " (" + queries + " queries)")

        case PropertyResult.Failure(property, paths, queries, elapsed, error) =>
          Console.println(consoleCtx.red("✗ ") + property.law + " (" + property.loc.format + ")" + " (" + queries + " queries)")

        case PropertyResult.Unknown(property, paths, queries, elapsed, error) =>
          Console.println(consoleCtx.red("? ") + property.law + " (" + property.loc.format + ")" + " (" + queries + " queries)")
      }
    }
    val timeInMiliseconds = f"${totalElapsed(results).toDouble / 1000000000.0}%3.1f"
    Console.println()
    Console.println(s"Result: ${numberOfSuccess(results)} / ${results.length} properties proven in $timeInMiliseconds second. (${totalPaths(results)} paths, ${totalQueries(results)} queries) ")
    Console.println()
  }

}
