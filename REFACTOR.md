# REFACTOR.md

One section per milestone. Fill each one in as you go, in order.

Milestone 1 is written in two sittings, the pin before the refactor and the
rest after. A pin written afterwards is worth nothing, and a TA will ask.

Keep it short and specific. Point at methods, call sites, and test names.

---

## Milestone 1: Direct a refactor, characterization first

### The pin (write this section before you direct the refactor)

**The pin.** File and test name, plus one sentence naming the method and the
observable result it pins. Not "recurring bookings work". Green against the
shipped code, and you did not edit or delete an existing test method to get
there.

`src/test/java/edu/cmu/cs214/scheduling/workflow/RecurringBoundaryPinTest.java`,
`recurringSubmitSkipsAWeekThatStartsWhenAnotherBookingEnds`: when
`BookingWorkflow.submit` books a RECURRING series (Mon 10:00–11:00 from Oct 5,
two weeks) into a room with a REGULAR booking from 9:00 to 10:00 on Oct 12,
week 1 is booked, the Oct 12 10:00 week lands in `getSkipped()`, and the
outcome is still accepted ("1 booked, 1 skipped"). Added as a new class in
commit `16f12fd`, on top of the initial commit. No existing test was touched;
36/36 green.

**Why that one, and does a shipped test already cover it?** Of everything
`BookingWorkflow` does, why is this the behavior worth a test? If something
shipped comes close, say what your pin adds. If nothing does, say how you
checked.

`submit` repeats one interval-overlap check three times, but not
consistently. REGULAR and BLOCKED use strict `<`, so touching slots are free,
while RECURRING uses `<=`, so touching slots count as a conflict. The obvious
refactor of that `switch` (extract an `overlaps()` helper, or one handler per
type) would merge those three copies, and the RECURRING boundary would quietly
become `<`. The closest shipped test is
`BookingWorkflowTest.regularSubmitAcceptsASlotThatStartsWhenAnotherEnds`, but
it only exercises REGULAR. The only series test,
`recurringSubmitBooksEveryWeekOfAnOpenSeries`, runs against an empty room. I
read every method in `BookingWorkflowTest`, and none puts a series next to an
existing booking. I also checked by mutation: changing the two RECURRING
`<= 0` comparisons in `submit` to `< 0` leaves all 35 shipped tests green and
fails only the pin (`expected: <1> but was: <2>`).

**What a regeneration would do differently here.** Suppose someone
threw this class away and regenerated it from a one-line description of what a
booking workflow does. Name the decision that would be made a second time, and
say which way it would probably go.

Whether two slots that only touch (one ends at 10:00, the next starts at
10:00) conflict. A regeneration would pick that rule again, once, for every
booking type, and it would almost certainly pick half-open intervals (`<`),
so back-to-back is allowed everywhere. That matches the REGULAR test, so the
shipped suite would pass, but series would start booking weeks the current
code skips. Whether `<=` is a deliberate buffer between series meetings or a
bug, the pin makes changing it a decision someone has to make on purpose.

### The directive

**The refactor and the exact directive.** Name the refactor (one from the menu
in the handout) and paste the directive you gave the agent, including the scope
you set, meaning which files and packages were in bounds, which were not, and
one line on why the boundary sits where it does.

Refactor: **Extract Method**, confined to `BookingWorkflow`. Directive given to
the agent, verbatim:

```
Refactor: Extract Method, in BookingWorkflow only.

Scope
- In bounds: src/main/java/edu/cmu/cs214/scheduling/workflow/BookingWorkflow.java.
- Out of bounds: every other file. Do not edit domain/, notify/, pricing/,
  reporting/, pom.xml, or anything under src/test/. Do not add new classes or files.
- Why: every store write and notification already goes through BookingWorkflow,
  so a behavior-preserving cleanup of it should never need another file. A change
  anywhere else means the refactor has turned into a redesign.

What to do
- Extract each case of the switch in submit, cancel, and priceOf into its own
  private method (e.g. submitRegular, submitRecurring, submitBlocked,
  cancelRecurring), so each public method reads as a short dispatch.
- Extract code that is duplicated word for word into private helpers. Examples
  are the member-and-capacity validation shared by REGULAR and RECURRING, and
  the roomName lookup in cancel and describe.
- Keep the switch statements. Do not replace them with polymorphism, enums with
  behavior, or strategy classes. That is a different refactor.

What must not change
- Public method signatures, constructor, and constants.
- Every returned message, notification recipient, subject, and body string,
  character for character.
- The order of calls to store.nextBookingId(), store.nextSeriesId(),
  store.save(), and hub.publish(), including which calls happen before a
  rejection can return.
- Every comparison operator, exactly as written. The overlap checks are NOT all
  the same: RECURRING uses <= and REGULAR/BLOCKED use <. Do not merge code that
  only looks duplicated. If two blocks differ in any operator or condition,
  either keep them separate or pass the difference in explicitly.

Done means
- JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 mvn -B test shows
  Tests run: 36, Failures: 0, Errors: 0, Skipped: 0.
- git diff --stat lists BookingWorkflow.java and nothing else.
- Do not commit. Stop and show me the diff.
```

Why the boundary sits there: `BookingWorkflow` is the single class every store
write and notification goes through, so a behavior-preserving Extract Method
has no reason to touch any other file.

### The result

**The diff and the suite.** How you are showing the diff to the TA (a commit,
`git diff`, a branch), and the totals line (the shipped count plus your pin,
all green).

The refactor is its own commit, `e767d85` ("Extract Method refactor of
BookingWorkflow"), directly after the directive commit `8f9ba20`. To show it:
`git show e767d85`, or `git diff 8f9ba20 e767d85`. Totals, locally and in
GitHub CI run 37956969682: `Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`,
`BUILD SUCCESS` (the shipped 35 plus `RecurringBoundaryPinTest`).

**What did NOT change: behavior and files.** The observable behavior you
checked is still the same, including anything that surprised you while reading.
Which files outside the scope are untouched, and how you verified that rather
than assumed it. If the agent reached outside the directive, say where and what
you did about it.

- *Behavior.* All 36 tests pass, including the pin, so the RECURRING `<=`
  boundary still holds. The pin still guards the refactored code: changing the
  two `<= 0` in the new `isTakenForSeries` to `< 0` fails only
  `recurringSubmitSkipsAWeekThatStartsWhenAnotherBookingEnds`
  (`expected: <1> but was: <2>`). I reverted that change.
- *Strings.* The set of distinct string literals in `BookingWorkflow.java` is
  identical before and after (`grep -o '"[^"]*"' | sort -u`, then `diff`). The
  only literals that disappeared were the second copies of the "unknown member"
  and capacity messages, now built once in `rejectMemberOrCapacity`.
- *Operators.* Before: two `<= 0` and seven `< 0`. After: two `<= 0` (both in
  `isTakenForSeries`) and three `< 0` (two in `overlaps`, one in
  `cancelRecurring`'s "skip earlier occurrences" check). The three strict
  overlap checks in REGULAR (room, then the member's other bookings) and
  BLOCKED all used the same pair of strict comparisons, so collapsing them into
  `overlaps` changes nothing.
- *Call order.* `nextBookingId`, `nextSeriesId`, `save`, and `publish` happen
  in the same order and behind the same rejections. `nextSeriesId` is still
  only called after every series check passes.
- *Surprises kept as they were.* These all came up while reading and are
  preserved: a series never checks whether the member is booked elsewhere
  (REGULAR does); cancelling one occurrence also cancels every *later* one,
  but no earlier ones; and a series where every week is skipped still comes
  back `isAccepted()`.
- *Files.* `git diff --stat 8f9ba20 e767d85` lists only `BookingWorkflow.java`.
  `git diff --stat 16f12fd e767d85`, excluding `REFACTOR.md` and
  `BookingWorkflow.java`, prints nothing, so no test, `domain/`, `notify/`,
  `pricing/`, `reporting/`, or `pom.xml` file changed. The agent did not reach
  outside the directive.

**One thing the agent changed that you had to look at twice.** Something you
checked line by line before accepting. If there was nothing, say how carefully
you read the diff.

The member check in `submitRegular`. The original compared against `held`
(every non-cancelled booking the member holds, across all rooms), not
`existing` (active bookings in this room). After the refactor it calls
`overlaps(held, slot)`, the same helper the room check uses. I checked that
the original operators in that loop were both strict `<`, not `<=`, so sharing
the helper is safe, and that the `isCancelled()` / member-id `continue` stayed
in front of it. A related point: `submitRegular` now calls `member.getId()`
with no null check of its own. That is safe only because
`rejectMemberOrCapacity` returns a rejection for a null member, and
`submitRegular` returns that rejection before reaching the loop.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

**What would flip your answer.** A condition about the artifact, not a feeling.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

**Would you apply it today?** Yes or no, one line, with the reason.
