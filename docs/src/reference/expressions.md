# Expressions

Expressions can be used to access variables and calculate values dynamically.

The following attributes of BPMN elements **require** an expression:
* Sequence Flow on an Exclusive Gateway: [condition](/bpmn-workflows/exclusive-gateways/exclusive-gateways.html#conditions)
* Message Catch Event / Receive Task: [correlation key](/bpmn-workflows/message-events/message-events.html#messages)
* Multi-Instance Activity: [input collection](/bpmn-workflows/multi-instance/multi-instance.html#defining-the-collection-to-iterate-over), [output element](/bpmn-workflows/multi-instance/multi-instance.html#collecting-the-output)
* Input/Output Variable Mappings: [source](/reference/variables.html#inputoutput-variable-mappings)

Additionally, the following attributes of BPMN elements can define an expression **optionally** instead of a static value:
* Message Catch Event / Receive Task: [message name](/bpmn-workflows/message-events/message-events.html#messages)
* Timer Catch Event: [timer definition](/bpmn-workflows/timer-events/timer-events.html#timers)
* Service Task: [job type](/bpmn-workflows/service-tasks/service-tasks.html#task-definition), [job retries](/bpmn-workflows/service-tasks/service-tasks.html#task-definition)
* Call Activity: [process id](/bpmn-workflows/call-activities/call-activities.html#defining-the-called-workflow)

## Expressions vs. Static Values

Some attributes of BPMN elements, like the timer definition of a timer catch event, can be defined either as a static value (e.g. `PT2H`) or as an expression (e.g. `=remaingTime`).

The value is identified as expression if it starts with an **equal sign** `=` (i.e. the expression prefix). The text behind the equal sign is the actual expression. For example, `=remaingTime` defines the expression `remaingTime` that access a variable with the name `remaingTime`.

If the values doesn't have the prefix then it is used as static value. A static value is used either as a string (e.g. job type) or as a number (e.g. job retries). A string value must not be wrapped inside quotes.

Note that an expression can also define a static value by using literals (e.g. `"foo"`, `21`, `true`, `[1,2,3]`, `{x: 22}`, etc.).

## The Expression Language

An expression is written in **FEEL** (Friendly Enough Expression Language). FEEL is part of the OMG's DMN (Decision Model and Notation) specification. It is designed to have the following properties:

* Side-effect free
* Simple data model with JSON-like object types: numbers, dates, strings, lists, and contexts
* Simple syntax designed for business professionals and developers
* Three-valued logic (true, false, null)

Zeebe integrates the [Feel-Scala](https://github.com/camunda/feel-scala) engine to evaluate FEEL expressions. The following sections cover common use cases in Zeebe. A complete list of supported expressions can be found in the project's [documentation](https://camunda.github.io/feel-scala).

### Access Variables

An expression can access a variable by its name.

```
owner
// "Paul"

totalPrice
// 21.2

items
// ["item-1", "item-2", "item-3"]
```

If the variable is a JSON document/object then it is handled as a FEEL context. A context property can be accesses by `.` (a dot) and the property name.

```
order.id
// "order-123"

order.customer.name
// "Paul"
```

### Boolean Expressions

An expression can use the following operators to compare two values:

<table style="width:100%">
  <tr>
    <th>Operator</th>
    <th>Description</th>
    <th>Example</th>
  </tr>

  <tr>
    <td>= (only <b>one</b> equal sign)</td>
    <td>equal to</td>
    <td>owner = "Paul"</td>
  </tr>

  <tr>
    <td>!=</td>
    <td>not equal to</td>
    <td>owner != "Paul"</td>
  </tr>

  <tr>
    <td>&#60;</td>
    <td>less than</td>
    <td>totalPrice &#60; 25</td>
  </tr>

  <tr>
    <td>&#60;=</td>
    <td>less than or equal to</td>
    <td>totalPrice &#60;= 25</td>
  </tr>

  <tr>
    <td>&#62;</td>
    <td>greater than</td>
    <td>totalPrice &#62; 25</td>
  </tr>

  <tr>
    <td>&#62;=</td>
    <td>greater than or equal to</td>
    <td>totalPrice &#62;= 25</td>
  </tr>

   <tr>
    <td>between _ and _</td>
    <td>same as <i>(x &#62;= _ and x &#60;= _)</i></td>
    <td>totalPrice between 10 and 25</td>
   </tr>

</table>

Multiple boolean values can be combined as disjunction (`and`) or conjunction (`or`).

```
orderCount >= 5 and orderCount < 15

orderCount > 15 or totalPrice > 50
```

If a variable or a nested property can be `null` then it can be compared to the `null` value. Comparing `null` to a value different from `null` results in `false`.

```
order = null
// true if order is null

totalCount > 5
// false is totalCount is null
```

### String Expressions

* string concat
* values to string

### Date-Time Expressions

* date and time
* duration
* cycle

### List Expressions

An element of a list can be accessed by its index. The index starts at `1`. A negative index starts at the end by `-1`. If the index is out of the range of the list then a `null` is returned instead.

```
["a","b","c"][1]
// "a"

["a","b","c"][2]
// "b"

["a","b","c"][-1]
// "c"
```

A list value can be filtered using a boolean expression. The result is a list of elements that fulfill the condition. The current element in the condition is assigned to the variable `item`.

```
[1,2,3,4][item > 2]
// [3,4]
```

The operators `every` and `some` can be used to test if all elements or at least one element of a list fulfill a given condition.

```
every x in [1,2,3] satisfies x >= 2
// false

some x in [1,2,3] satisfies x > 2
// true
```

### Invoke Functions

FEEL defines a set of [built-in functions](https://camunda.github.io/feel-scala/feel-built-in-functions) that can be invoked in an expression.

```
contains("foobar", "foo")
// true

floor(1.5)
// 1

count(["a","b","c"])
// 3

append(["a","b"], "c")
// ["a","b","c"]
```

## Additional Resources

References:
* [FEEL-Scala - Documentation](https://camunda.github.io/feel-scala)
* [FEEL - Data Types](https://camunda.github.io/feel-scala/feel-data-types)
* [FEEL - Expressions](https://camunda.github.io/feel-scala/feel-expression)
* [FEEL - Built-in Functions](https://camunda.github.io/feel-scala/feel-built-in-functions)
* [DMN Specification](https://www.omg.org/spec/DMN/About-DMN/)
