// !RENDER_DIAGNOSTICS_MESSAGES
// AMBIGUOUS_ACTUALS: bottom, middle
package foo

expect class <!AMBIGUOUS_ACTUALS("Class 'A'; bottom, middle"), LINE_MARKER("descr='Has actuals in middle module'")!>A<!>
