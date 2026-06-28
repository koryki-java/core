# Type Resolution

## ai.koryki.catalog.schema.types.TypeDescriptor

- physicalTypeName:String
- typeFamily:TypeFamily
- typeEncoding:TypeEncoding
- precision:int
- scale:int

## ai.koryki.iql.types.ExpressionTypeResolver

A recursive walk over the expression tree that dispatches by expression kind:

- literal → a constant descriptor ("abc"→TEXT, BigInteger→INTEGER, 12.34→DECIMAL(4,2) carrying its own precision/scale, date/time/timestamp/duration→their types, null→the no-family NULL).
- field → LinkResolver → schema column → its declared TypeDescriptor (this is where encodings like the seconds-from-midnight TIME enter — declared metadata, trusted).
- function → FunctionRenderer.descriptor(FunctionBinding) → the function's ReturnTypeInference. The binding carries a this::resolve callback, so a function's return type can recurse into its operands' types.
- subquery / block field → a child() resolver bound to a nested visibility scope.

## ai.koryki.iql.functions.FunctionBinding

Binds ai.koryki.iql.query.Function-Object to a Resolver-Function<Expression, TypeDescriptor>.
The only constructor in productive code is: ai.koryki.iql.types.ExpressionTypeResolver.resolveFunction

## ai.koryki.iql.functions.FunctionCatalog
