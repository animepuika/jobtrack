# Lesson 1: a `Job` class in plain Java

**Objective:** model a job as a Java class, keep a few in a list, and print them,
with no frameworks and no build tool. Just `javac` and `java`.

## Run it

From this folder, in PowerShell:

```powershell
javac -d out src/jobtrack/*.java
java -cp out jobtrack.Main
```

Expected output:

```text
Junior Java Developer at Example Logistics (https://example.com/jobs/101)
Backend Intern at Sample Health (https://example.com/jobs/202)
Tracked jobs: 2
```

`out/` contains compiled `.class` files; they are ignored by Git.

## Concepts

### From TypeScript to Java

```ts
class Job {
  constructor(
    private readonly title: string,
    private readonly company: string,
    private readonly sourceLink: string,
  ) {}
}
```

Java has no constructor parameter properties. Fields are declared separately and
assigned in the constructor; see [Job.java](src/jobtrack/Job.java).

| TypeScript | Java | Difference |
| --- | --- | --- |
| `private` | `private` | TypeScript's check is erased at runtime; Java's is enforced by the compiler and the JVM. |
| No modifier = public | No modifier = **package-private** | Visible only inside the same package. Write `public` explicitly when you mean it. |
| `readonly` | `final` | Must be assigned exactly once, in the declaration or every constructor. |
| `string` | `String` | A class, not a primitive. Compare text with `.equals(...)`, never `==`. |
| `toString()` | `@Override public String toString()` | `@Override` asks the compiler to confirm you really override a parent method. |
| `job.title` | `job.getTitle()` | Java convention: private fields, public getter methods. |

Trying `job.title` from `Main` fails at compile time:

```text
error: title has private access in Job
```

### Constructors

- A constructor has the class's name and no return type.
- `this.title = title` copies the parameter into the field; the parameter shadows
  the field, so `this.` is required.
- Once you write any constructor, Java no longer provides the empty default one.
  `new Job()` would not compile.

### Packages and files

- `package jobtrack;` puts both classes in the `jobtrack` package, and the files
  must sit in a matching `jobtrack/` folder.
- A `public` class must be in a file with the same name: `Job` in `Job.java`.
- The class's full name is `jobtrack.Main`, which is what `java` needs to run it.
- Classes in the same package see each other without imports. `List` and
  `ArrayList` live in `java.util`, so `Main` imports them.

### Compilation

```text
Job.java, Main.java  --javac-->  Job.class, Main.class (bytecode)  --java (JVM)-->  output
```

- `javac -d out` writes bytecode into `out/`, in package folders.
- `java -cp out jobtrack.Main` sets the classpath (where to find classes) and
  starts `public static void main(String[] args)`.
- `static` means `main` belongs to the class itself; the JVM calls it without
  creating a `Main` object.
- Bytecode carries a version: Java 25 produces class file version 69. An older JVM
  refuses newer bytecode, which is the error seen in Milestone 0.

### Collections

- `List<Job>` is an **interface** (the contract); `ArrayList<>` is an
  **implementation**. Declaring the variable as `List` lets the implementation
  change later without touching code that uses it.
- `<>` (the diamond) lets Java infer `ArrayList<Job>` from the left side.
- `jobs.add(...)` is like `push`; `jobs.size()` is like `.length`.
- `for (Job job : jobs)` is the equivalent of `for (const job of jobs)`.
- Generics only take object types: `List<int>` does not compile; use `List<Integer>`.
- `List.of(a, b)` creates a list that cannot be changed; `add` on it throws an
  exception at runtime.

## Exercise

Change `Job` so that:

1. It has a **location** (for example `"Riga"` or `"Remote"`), passed through the
   constructor, readable with a getter, and included in `toString()`.
2. Creating a job with a **blank title** (`""`, `"   "`) is rejected by throwing an
   exception. The object should never exist in an invalid state.

Then, in `Main`:

- Give the two existing jobs a location.
- Try creating one job with a blank title and show that it fails. Print a message
  instead of letting the program crash. TypeScript's `try/catch` maps directly.

Hints, not solutions:

- `String` has a method that answers "is this empty or only whitespace?". Look
  for it in your editor's autocomplete on a `String` value.
- Java's standard exception for "you passed a bad argument" is in `java.lang`, so
  it needs no import. Its name says what it is for.
- Decide what should happen to a `null` title, and be ready to explain why.

Done when it compiles, the output shows locations, and the blank title produces
your message rather than a stack trace. Then ask for a review.
