# Requirements Document

## Introduction

This feature extends the OpenSCAD parser to support `module` definitions, module calls (user-defined shape instantiation), and `for` loops. It also fixes a critical bug in `skipToNextStatement()` that causes unrecognized top-level constructs to consume the remainder of the file. Together, these changes enable the parser to handle modular, parameterized OpenSCAD programs that use iteration.

## Glossary

- **Parser**: The `OpenSCADParser` class responsible for converting OpenSCAD source text into a `SceneNode` tree.
- **Module_Definition**: A named, reusable block of OpenSCAD statements declared with the `module` keyword, optionally accepting parameters with default values.
- **Module_Call**: An invocation of a previously defined module by name, passing arguments that bind to the module's parameters.
- **For_Loop**: A control structure that repeats its body for each value in a numeric range, binding the loop variable to successive values.
- **Range_Expression**: A bracketed numeric range in the form `[start : end]` or `[start : step : end]` that produces a sequence of values.
- **SceneNode_Tree**: The hierarchical data structure (`SceneNode` sealed class) representing parsed geometry.
- **Skip_Recovery**: The mechanism (`skipToNextStatement()`) that advances the parser past an unrecognized construct to resume parsing.

## Requirements

### Requirement 1: Module Definition Parsing

**User Story:** As a user, I want to define reusable modules in my OpenSCAD code, so that I can organize complex models into named components.

#### Acceptance Criteria

1. WHEN the Parser encounters `module <name>() { <body> }`, THE Parser SHALL store the Module_Definition with its name, an empty parameter list, and its body statements.
2. WHEN the Parser encounters `module <name>(<param1>, <param2>, ...) { <body> }`, THE Parser SHALL store the Module_Definition with its name, the ordered parameter list preserving declaration order, and its body statements.
3. WHEN the Parser encounters a Module_Definition with default parameter values in the form `<param> = <expr>`, THE Parser SHALL store the default value for each such parameter while preserving parameters without defaults in their declared positions.
4. WHEN the Parser stores a Module_Definition, THE Parser SHALL NOT produce any SceneNode in the output tree for the definition itself.
5. WHEN the Parser encounters a Module_Definition with a name that duplicates an earlier definition, THE Parser SHALL replace the earlier definition with the new one.
6. IF the Parser encounters a `module` keyword followed by a malformed definition (missing name, unbalanced braces, or missing opening brace), THEN THE Parser SHALL skip the malformed construct using Skip_Recovery and continue parsing subsequent statements without storing a Module_Definition.
7. WHEN the Parser encounters a Module_Definition whose parameter list contains a mix of parameters with and without default values, THE Parser SHALL accept parameters in any order regardless of whether they have defaults.

### Requirement 2: Module Call Expansion

**User Story:** As a user, I want to call my defined modules by name with arguments, so that I can reuse geometry without duplicating code.

#### Acceptance Criteria

1. WHEN the Parser encounters a Module_Call with a name matching a stored Module_Definition, THE Parser SHALL expand the module body into the SceneNode_Tree at the call site by parsing the body statements with the bound parameter values substituted for parameter references.
2. WHEN the Parser expands a Module_Call with positional arguments, THE Parser SHALL bind each argument value to the corresponding parameter by position (first argument to first parameter, second to second, and so on).
3. WHEN the Parser expands a Module_Call with fewer arguments than defined parameters, THE Parser SHALL use the default value for each unbound parameter that has a default defined.
4. IF the Parser expands a Module_Call where a parameter has no corresponding positional argument and no default value is defined, THEN THE Parser SHALL bind that parameter to the value 0.
5. IF the Parser encounters a Module_Call with a name that does not match any stored Module_Definition or built-in operation, THEN THE Parser SHALL skip the call without producing a SceneNode and continue parsing subsequent statements.
6. WHEN a Module_Call appears as a child statement (e.g., inside a transform or CSG block), THE Parser SHALL expand it in the same position a built-in primitive would occupy.
7. WHEN a Module_Definition body contains calls to other defined modules, THE Parser SHALL recursively expand those nested Module_Calls up to a maximum depth of 256 levels.
8. IF recursive Module_Call expansion exceeds 256 levels, THEN THE Parser SHALL stop expansion of that call, produce no SceneNode for it, and continue parsing subsequent statements.
9. IF the Parser encounters a Module_Call with more positional arguments than the Module_Definition has parameters, THEN THE Parser SHALL ignore the excess arguments and bind only those matching defined parameters by position.

### Requirement 3: For Loop Parsing and Unrolling

**User Story:** As a user, I want to use `for` loops to repeat geometry with varying parameters, so that I can create patterns without manually duplicating statements.

#### Acceptance Criteria

1. WHEN the Parser encounters `for (<var> = [<start> : <end>]) { <body> }`, THE Parser SHALL unroll the loop body for each integer value of `<var>` from `<start>` to `<end>` inclusive with step 1.
2. WHEN the Parser encounters `for (<var> = [<start> : <step> : <end>]) { <body> }`, THE Parser SHALL unroll the loop body for each value of `<var>` from `<start>` to `<end>` inclusive, incrementing by `<step>`, where values are computed as floating-point numbers.
3. WHEN the Parser unrolls a For_Loop iteration, THE Parser SHALL bind the loop variable to its current iteration value within the loop body scope, and restore any previously existing variable of the same name after the loop completes.
4. WHEN the For_Loop body is a single statement without braces, THE Parser SHALL treat it as the loop body (consistent with OpenSCAD syntax allowing `for (...) statement;`).
5. WHEN the Parser completes unrolling a For_Loop, THE Parser SHALL produce a Group SceneNode whose children list contains one child per iteration, in iteration order from `<start>` toward `<end>`.
6. WHEN a For_Loop is nested inside another For_Loop, THE Parser SHALL bind each loop's variable independently, producing one Group child for each combination of outer and inner iteration values.
7. IF the computed iteration count of a For_Loop exceeds 10000, THEN THE Parser SHALL stop unrolling at 10000 iterations and discard remaining iterations.

### Requirement 4: Skip Recovery Fix

**User Story:** As a user, I want the parser to continue producing geometry from statements after an unrecognized construct, so that a single unsupported feature does not discard the rest of my model.

#### Acceptance Criteria

1. WHEN the Skip_Recovery is invoked, THE Skip_Recovery SHALL initialize its depth counter to zero and begin scanning tokens from the current parser position.
2. WHEN the Skip_Recovery encounters an opening bracket (`{`, `(`, or `[`), THE Skip_Recovery SHALL increment its depth counter.
3. WHEN the Skip_Recovery encounters a closing bracket (`}`, `)`, or `]`) and depth is greater than zero, THE Skip_Recovery SHALL decrement its depth counter and continue scanning.
4. WHEN the Skip_Recovery encounters a semicolon at depth zero, THE Skip_Recovery SHALL stop and position the parser immediately after the semicolon.
5. WHEN the Skip_Recovery encounters a closing `}` that brings depth from one to zero, THE Skip_Recovery SHALL stop and position the parser immediately after that closing brace.
6. IF the Skip_Recovery encounters a closing bracket (`)`, `]`, or `}`) at depth zero without a preceding matching open bracket, THEN THE Skip_Recovery SHALL stop and position the parser immediately after that closing bracket.
7. IF the Skip_Recovery reaches the end of input without encountering a termination condition, THEN THE Skip_Recovery SHALL stop with the parser positioned at the end of input, and THE Parser SHALL produce no further output.
8. WHEN a balanced block construct (e.g., `unknown() { ... }`) is encountered at the top level, THE Skip_Recovery SHALL skip exactly that construct and THE Parser SHALL resume parsing the next statement immediately following it.

### Requirement 5: Range Expression Evaluation

**User Story:** As a user, I want to use range expressions with arithmetic in loop bounds, so that I can compute loop limits from variables.

#### Acceptance Criteria

1. WHEN the Parser encounters a Range_Expression `[<start> : <end>]`, THE Parser SHALL evaluate `<start>` and `<end>` as arithmetic expressions (including variable references) and produce a sequence beginning at `<start>`, incrementing by 1, including each value less than or equal to `<end>`.
2. WHEN the Parser encounters a Range_Expression `[<start> : <step> : <end>]`, THE Parser SHALL evaluate all three components as arithmetic expressions and produce a sequence beginning at `<start>`, incrementing by `<step>`, including each value where `value <= <end>` (for positive step) or `value >= <end>` (for negative step).
3. IF the step value in a Range_Expression evaluates to zero, THEN THE Parser SHALL produce an empty sequence (no iterations).
4. IF the step is positive and start is greater than end, THEN THE Parser SHALL produce an empty sequence.
5. IF the step is negative and start is less than end, THEN THE Parser SHALL produce an empty sequence.
6. IF a variable referenced within a Range_Expression's start, step, or end component is not defined, THEN THE Parser SHALL treat that variable's value as 0.
