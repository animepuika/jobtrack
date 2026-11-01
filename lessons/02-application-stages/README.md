# Lesson 2: application stages

**Objective:** track where an application stands, and let Java refuse stage
changes that make no sense, such as an offer for a job you only saved.

## Run it

From this folder, in PowerShell:

```powershell
javac -d out src/jobtrack/*.java
java -cp out jobtrack.Main
```

Expected output:

```text
Tracking: Junior Java Developer at Example Logistics, Riga (https://example.com/jobs/101)
Possible stages: SAVED APPLIED INTERVIEW OFFER ACCEPTED REJECTED WITHDRAWN
Starts at: SAVED
  SAVED -> APPLIED: moved
  APPLIED -> OFFER: refused (Cannot move from APPLIED to OFFER)
  APPLIED -> INTERVIEW: moved
  INTERVIEW -> OFFER: moved
  OFFER -> ACCEPTED: moved
  ACCEPTED -> WITHDRAWN: refused (Cannot move from ACCEPTED to WITHDRAWN)
Final stage: ACCEPTED
History (5 entries):
  2026-10-20 SAVED
  2026-10-21 APPLIED
  2026-10-28 INTERVIEW
  2026-11-05 OFFER
  2026-11-07 ACCEPTED
Editing the history from outside was blocked
History still has 5 entries
```

The history lines come from the exercise solution below.

## Files

| File | Role |
| --- | --- |
| [Job.java](src/jobtrack/Job.java) | Copied unchanged from Lesson 1 |
| [Stage.java](src/jobtrack/Stage.java) | The allowed stages, and which moves between them are allowed |
| [Application.java](src/jobtrack/Application.java) | One tracked application: its job, current stage, and history |
| [StageChange.java](src/jobtrack/StageChange.java) | One history entry: a stage and the date it was reached (exercise solution) |
| [Main.java](src/jobtrack/Main.java) | Walks one fictional application through its stages |

## The stage rules

```text
SAVED ──> APPLIED ──> INTERVIEW ──> OFFER ──> ACCEPTED
  │          │            │           │
  └──────────┴────────────┴───────────┴──> WITHDRAWN   (you stop)
             └────────────┴───────────┴──> REJECTED    (they stop)
```

`ACCEPTED`, `REJECTED`, and `WITHDRAWN` are final: nothing moves out of them.
`SAVED` means "I'm interested", not "I applied", so it can't be rejected.

## Concepts

### Enums

```ts
enum Stage { SAVED, APPLIED, INTERVIEW }
```

In TypeScript an enum compiles to numbers or strings. In Java each constant,
such as `Stage.SAVED`, is an **object** of the `Stage` class:

- An enum can have methods. `Stage` has `canMoveTo(...)`, so the rules live with
  the stages themselves.
- `Stage.values()` returns every constant in declaration order. `Main` uses it to
  print the list.
- Printing a constant prints its name: `SAVED`.
- Compare enum values with `==`. Each constant exists exactly once, so `==` is
  safe here, unlike with `String`, where you need `.equals(...)`.
- Only the listed constants can exist. Nobody can create a `new Stage()`, and a
  typo like `Stage.APLIED` does not compile.

### Switch expressions

```java
return switch (this) {
    case SAVED -> next == APPLIED || next == WITHDRAWN;
    ...
    case ACCEPTED, REJECTED, WITHDRAWN -> false;
};
```

- `this` is the stage the method was called on: in `Stage.SAVED.canMoveTo(...)`,
  `this` is `SAVED`.
- The `switch` **returns a value**, so it can follow `return` directly.
- `->` cases do not fall through, so no `break` is needed, unlike the classic
  `switch` in both TypeScript and Java.
- One case can list several constants, separated by commas.
- There is no `default`. Java checks that every constant is covered. Adding a new
  `GHOSTED` stage without a case stops compilation:

  ```text
  error: the switch expression does not cover all possible input values
  ```

  This is deliberate. With `default`, a new stage would silently get the
  default behavior.

### An object holding an object

`Application` has a `Job` field. This is the first relationship in JobTrack:
each application points to the job it is for. Later, in the database, this
becomes a foreign key.

### State that changes, but only by the rules

- `job` is `final`: an application never switches to a different job.
- `stage` is **not** `final`, because it is meant to change.
- There is no `setStage(...)`. The only way to change the stage is `moveTo(...)`,
  which checks the rules first. Other code cannot skip the check.
- Every new `Application` starts at `SAVED`. The constructor sets it, so callers
  cannot pick a starting stage.

### Two kinds of "no"

| Exception | Meaning | Used for |
| --- | --- | --- |
| `IllegalArgumentException` | "The value you passed is wrong." | A blank title; a missing job |
| `IllegalStateException` | "That's not allowed right now." | `APPLIED -> OFFER`. The move might be fine later, from another stage |

Using the right one makes errors easier to understand, and later lets Spring
turn them into different HTTP responses.

### A `private static` helper

`tryMove(...)` in `Main` avoids repeating the same `try/catch` six times.

- `static`: it belongs to `Main` itself, like `main`, so it can be called
  without creating a `Main` object.
- `private`: only `Main` can use it.
- It saves the stage **before** the move, so a refused move can say where it was
  refused from.

## Exercise

Give each application a **history** of its stage changes.

1. Create a small class for one history entry, holding the stage moved to and the
   date it happened. Use `java.time.LocalDate` for the date. `LocalDate.now()`
   gives today's date, and `LocalDate.of(2026, 11, 1)` gives a fixed date.
2. Give `Application` a list of these entries. Record the starting `SAVED` stage
   in the constructor, and add an entry on every **successful** `moveTo(...)`. A
   refused move must not add an entry.
3. Add a method that lets other code read the history, without letting it add or
   remove entries. Think about what happens if you return the list itself.
4. In `Main`, print the full timeline at the end, one line per entry, for example:

   ```text
   2026-11-01 SAVED
   2026-11-01 APPLIED
   ```

Hints, not solutions:

- `LocalDate` needs `import java.time.LocalDate;`. Printing one gives
  `2026-11-01`.
- `moveTo(...)` currently changes one thing. After the rule check, it should change
  two.
- For step 3, look at what `List.copyOf(...)` returns, and remember from Lesson 1
  what happens when you call `add` on an unmodifiable list.
- Passing the date into `moveTo(...)` makes `Main` able to show a realistic
  timeline with different dates. Using `LocalDate.now()` inside is simpler. Pick
  one and be ready to explain the tradeoff.

Done when it compiles, the timeline prints, and the refused moves are missing
from it.

### Solution notes

- `StageChange` is a **record**: a compact, immutable data class. Java generates
  the constructor, the accessors `stage()` and `date()`, `equals`, `hashCode`, and
  `toString` from the header. Its compact constructor rejects a missing stage or
  date; its `toString` is overridden to print `2026-10-20 SAVED`.
- `Application` keeps a private `ArrayList`. The constructor records `SAVED`;
  `moveTo` adds an entry only after the rule check passes.
- `getHistory()` returns `List.copyOf(history)`, an unmodifiable copy. Returning
  the field itself would let callers add or clear entries. `Main` shows an outside
  `add` failing with `UnsupportedOperationException`.
- Dates are passed in rather than read with `LocalDate.now()`. Output is the same on
  every run, and future tests can check exact dates. The cost is that callers must
  supply a date.
- Not handled yet: a date earlier than the previous entry. That rule, and
  corrections to the history, belong with persistence in Milestone 3.
