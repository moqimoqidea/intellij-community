// "Import" "false"
// ACTION: Convert to lazy property
// ACTION: Convert property initializer to getter
// ACTION: Create class 'ExcludedClass'
// ACTION: Create function 'ExcludedClass'
// ACTION: Create test
// ACTION: Rename reference
// ERROR: Unresolved reference: ExcludedClass

val x = <caret>ExcludedClass()