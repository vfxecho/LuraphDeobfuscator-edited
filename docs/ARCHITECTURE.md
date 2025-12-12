# Luraph Deobfuscator Architecture

This document explains how the Luraph deobfuscator works end-to-end, from CLI entry points through the complex devirtualization pipeline to final output generation.

## Overview

The deobfuscator is a Java 8 command-line tool that processes Luraph-obfuscated Lua scripts through several stages:
1. **CLI Processing** - Command-line argument parsing and orchestration
2. **Lua Parsing** - ANTLR-based lexing and parsing to create parse trees
3. **AST Construction** - Converting parse trees to Abstract Syntax Trees
4. **AST Optimization** - Constant folding, propagation, and symbol renaming
5. **Devirtualization** - Complex VM analysis and chunk reconstruction
6. **Code Generation** - Final Lua bytecode output

## CLI Entry Points

**Primary class:** [`Main.java`](../src/Main.java)

The CLI provides several switches that control the processing pipeline:

- **`-i <file>`** (required) - Input Luraph file to process
- **`-p`** - Print the optimized VM source to stdout
- **`-c`** - Copy the optimized VM source to system clipboard  
- **`-s`** - Launch interactive AST viewer GUI
- **`-b`** - Print devirtualized luac to stdout
- **`-o <file>`** - Save devirtualized luac to specified output file

### CLI Processing Flow

1. **Parse Arguments** - Apache Commons CLI handles argument validation
2. **Generate Parse Tree** - Create ANTLR parse tree from input file
3. **Build AST** - Convert parse tree to AST using visitor pattern
4. **Optimize AST** - Run constant folding and propagation passes
5. **Rename Symbols** - Apply symbol table-driven renaming
6. **Devirtualize** - Extract VM semantics and reconstruct chunks
7. **Generate Output** - Handle requested output formats based on flags

## Lua Parsing Pipeline

**Grammar:** [`grammar/Lua.g4`](../grammar/Lua.g4)  
**Lexer:** [`LuaLexer.java`](../src/LuaLexer.java)  
**Parser:** [`LuaParser.java`](../src/LuaParser.java)  
**Visitor:** [`BuildASTVisitor.java`](../src/BuildASTVisitor.java)

The parsing stage uses ANTLR 4.8 to process Lua source:

1. **Lexical Analysis** - `LuaLexer` converts input characters into tokens
2. **Syntax Analysis** - `LuaParser` builds parse trees from token streams  
3. **AST Construction** - `BuildASTVisitor` traverses parse tree to create AST nodes

### AST Node Hierarchy

**Package:** [`ASTNodes`](../src/ASTNodes/) - Contains all AST node definitions

Core node types include:
- `Block` - Container for statements and return statements
- `Statement` subtypes - Assignment, function calls, control flow
- `Expression` subtypes - Literals, variables, binary operations
- `Function` - Function definitions with parameters and bodies

## AST Optimization and Renaming

**Manager:** [`ASTOptimizerMgr.java`](../src/ASTOptimizerMgr.java)  
**Optimizers:** [`ASTConstantFolder.java`](../src/ASTConstantFolder.java), [`ASTConstantPropagator.java`](../src/ASTConstantPropagator.java)  
**Renamers:** [`ASTBasicRenamer.java`](../src/ASTBasicRenamer.java), [`SymbolTable.java`](../src/SymbolTable.java)

### Optimization Pipeline

The `ASTOptimizerMgr` coordinates multiple optimization passes:

1. **Constant Folding** - Evaluate constant expressions at compile time
2. **Constant Propagation** - Replace variables with known constant values
3. **Symbol Renaming** - Use symbol tables to rename variables consistently

### Renaming Passes

Multiple renamer passes run in sequence:
- **Pass 1** - Initial symbol analysis and table construction
- **Pass 2** - Variable scope resolution and renaming
- **Pass 3** - Final symbol consolidation

The renamer uses scope-aware symbol tables to track variables across function boundaries and apply consistent renaming rules.

## Devirtualization Process

**Primary class:** [`LuraphDevirtualizer.java`](../src/LuraphDevirtualizer.java)

This is the most complex component, responsible for extracting the original Lua VM semantics from obfuscated code.

### Expected VM Architecture

The deobfuscator expects a specific Luraph VM structure:

**Core Functions:**
- **`vm_run`** - Main VM execution loop
- **`create_wrapper`** - Creates execution contexts
- **`get_byte/get_dword/get_bits/get_float64`** - Byte extraction helpers
- **`get_instruction`** - Instruction decoding
- **`get_string`** - String decryption
- **`decode_chunk`** - Metadata extraction

**VM Data Structures:**
- **Handler Tables** - `vm_handler_table`, `vm_opcode_handler_table`
- **Instruction Mapping** - Maps VM opcodes to handler functions
- **Encryption Keys** - Two-level key system for instruction/string decryption
- **Metadata Tables** - Constants, debug info, prototypes

### Devirtualization Stages

1. **Metadata Discovery** - Locate and extract chunk metadata
2. **Handler Mapping** - Build mapping from VM opcodes to handler functions
3. **Key Extraction** - Identify encryption keys for instruction/string decoding
4. **Instruction Decoding** - Decode VM instruction stream using extracted keys
5. **Chunk Reconstruction** - Build `LuaChunk` objects from decoded data
6. **Optimization** - Clean up reconstructed chunks

### VM Architecture Components

**Package:** [`LuaVM`](../src/LuaVM/)

- **`VMOp`** - Enumeration of Lua VM opcodes
- **`VMInstruction`** - Individual instruction representation
- **`VMBasicBlock`** - Basic block generation for control flow analysis
- **`VMControlFlowGraph`** - Control flow graph construction and analysis
- **`LuaChunk`** - Represents compiled Lua function chunks
- **`LuaValue`** - Lua value types (nil, boolean, number, string)

## VM Architecture Details

The deobfuscator expects the following Luraph VM structure:

### Handler Table System

The Luraph VM uses a two-level handler dispatch system:

1. **VM Handler Table** (`vm_handler_table`) - Maps internal VM opcodes to handler implementations
2. **Opcode Dispatcher Table** (`vm_opcode_handler_table`) - Maps Lua VM opcodes to VM handler indices

This indirection allows the obfuscator to remap opcodes while maintaining correct dispatch semantics.

### Encryption System

The VM uses a two-level encryption system:

- **Instruction Encryption** - VM instructions encrypted with key1
- **String Encryption** - String constants encrypted with key2  
- **Metadata Encryption** - Chunk metadata encrypted with derived keys

The deobfuscator must locate these keys by analyzing the deobfuscation code and evaluating constant expressions.

### Helper Functions

The expected helper functions include:

- **`get_byte`** - Extract single bytes from encrypted streams
- **`get_dword`** - Extract 32-bit values
- **`get_bits`** - Extract bit fields from instruction data
- **`get_float64`** - Extract double-precision floating point values
- **`get_string`** - Extract and decrypt strings
- **`get_instruction`** - Decode complete VM instructions

## Chunk Processing and Optimization

**Optimizer:** [`LuaChunkOptimizer.java`](../src/LuaChunkOptimizer.java)  
**Generator:** [`LuacGenerator.java`](../src/LuacGenerator.java)

### Chunk Optimization Pipeline

1. **Basic Block Generation** - Convert instruction stream to basic blocks
2. **Control Flow Analysis** - Build control flow graph
3. **Block Ordering** - Linearize CFG for bytecode generation
4. **Branch Target Fixup** - Correct branch offsets and targets
5. **Dead Code Elimination** - Remove zero-length jumps and unreachable code
6. **Anti-Symbolic Execution Cleanup** - Remove LuaVM sandbox detection tricks

### Special Optimizations

**Closure Anti-Symbolic Execution Trick Removal:**
Luraph injects specific instruction sequences to defeat symbolic execution tools. The optimizer detects and removes these patterns:
```java
// Pattern: CLOSURE, SETGLOBAL, GETGLOBAL, CALL, RETURN
// Replaced with: direct prototype extraction
```

**Global Integer Reference Normalization:**
Luraph replaces global variable names with integer indices. The optimizer restores proper string references required by the Lua VM.

## Code Generation

The `LuacGenerator` class writes standard Lua 5.1 bytecode (`.luac`) format:

### Output Format

1. **Header** - Lua signature (0x1B4C7561), version, format flags
2. **Chunk Data** - Function prototype information
3. **Instructions** - Optimized instruction stream
4. **Constants** - Literal values (numbers, strings, booleans)
5. **Prototypes** - Nested function definitions

### Binary Format Details

- **Big-endian header** with Lua 5.1 signature
- **Little-endian** instruction encoding and numeric data
- **Variable-length encoding** for strings with null terminators
- **Type-tagged constants** with appropriate serialization

## Processing Flow Example: luraphtest.luac

Let's trace how `luraphtest.luac` flows through the pipeline:

### Input Processing

```bash
java -jar LuraphDevirtualizer.jar -i luraphtest.luac -p -o output.luac
```

### Stage-by-Stage Flow

1. **CLI Parsing** - Validate arguments, locate input file
2. **Parse Tree Generation** - ANTLR creates parse tree from obfuscated source
3. **AST Construction** - `BuildASTVisitor` creates AST representation
4. **AST Optimization** - `ASTOptimizerMgr` applies constant folding/propagation
5. **Symbol Renaming** - Three-pass renaming for clarity
6. **Devirtualization** - `LuraphDevirtualizer.process()`:
   - Discover VM structure and handler tables
   - Extract encryption keys (key1, key2)
   - Decode instruction stream
   - Reconstruct function chunks
7. **Chunk Optimization** - `LuaChunkOptimizer.optimize()`:
   - Build basic blocks and CFG
   - Linearize and fix branch targets
   - Remove anti-analysis tricks
8. **Output Generation**:
   - **`-p` flag**: `ASTSourceGenerator` prints optimized source
   - **`-o` flag**: `LuacGenerator` writes `output.luac`

### Artifacts Generated

- **Source dump** (via `-p`): Human-readable Lua source after optimization
- **Binary output** (via `-o`): Standard Lua 5.1 bytecode file
- **AST viewer** (via `-s`): Interactive GUI showing AST structure
- **Console output** (via `-b`): Devirtualized instruction listing

## Key Classes and Responsibilities

### Parsing Layer
- **`LuaLexer`** - Tokenizes Lua source
- **`LuaParser`** - Builds parse trees  
- **`BuildASTVisitor`** - Converts parse trees to AST

### Optimization Layer
- **`ASTOptimizerMgr`** - Orchestrates optimization passes
- **`ASTConstantFolder`** - Constant expression evaluation
- **`ASTConstantPropagator`** - Variable value propagation
- **`SymbolTable`** - Scope-aware symbol tracking

### Devirtualization Layer  
- **`LuraphDevirtualizer`** - Main devirtualization coordinator
- **`RenamerPass1/2/3`** - Multi-phase symbol renaming
- **`LuraphDevirtualizer`** - VM analysis and chunk extraction

### VM Abstraction Layer
- **`LuaVM.VMOp`** - Lua VM opcode definitions
- **`LuaVM.VMInstruction`** - Instruction representation
- **`LuaVM.LuaChunk`** - Function chunk model
- **`LuaVM.VMControlFlowGraph`** - Control flow analysis

### Output Generation Layer
- **`LuaChunkOptimizer`** - Post-devirtualization optimization
- **`LuacGenerator`** - Binary bytecode generation
- **`ASTSourceGenerator`** - Source code generation
- **`CFGOrderer`** - Control flow graph ordering

### Utility Components
- **`ASTTreeViewer`** - Interactive AST visualization
- **`ClipboardUtils`** - System clipboard integration
- **`ConstantData`** - Constant value modeling

## Future Development Notes

This architecture provides a solid foundation for extending the deobfuscator:

- **Additional obfuscation techniques** can be handled by extending `LuraphDevirtualizer`
- **New optimization passes** can be plugged into `ASTOptimizerMgr`  
- **Alternative output formats** can be supported by implementing new generators
- **Enhanced analysis** can be added to the VM abstraction layer

The modular design allows for incremental improvements while maintaining the core devirtualization pipeline integrity.