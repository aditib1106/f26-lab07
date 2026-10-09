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

### Second refactor: replace conditional with polymorphism

**Why there is a second refactor.** Extract Method came first (`e767d85`,
above). It split each case into its own method, but `submit`, `cancel`,
`priceOf`, and `describe` still each switched on `BookingType`, so it did not
meet the handout's bar. I directed Replace Conditional with Polymorphism
second, on top of the Extract Method commit, to remove the type switches.
The pin (`16f12fd`) still predates both refactors.

**The exact directive.**

```
Second refactor on BookingWorkflow: replace the conditional with polymorphism. Create a package-private interface BookingTypeHandler with submit(BookingRequest, Room), cancel(Booking, String roomName, boolean adminOverride), priceOf(Booking) and describe(Booking, String roomName). Add RegularHandler, RecurringHandler and BlockedHandler in the same package. Move the bodies of the existing submitX, cancelX, priceOfX and describe cases into them. BookingWorkflow looks up the handler from a Map<BookingType, BookingTypeHandler>, and no method switches on type anymore. Keep the “unsupported booking type” and default fallbacks behaving identically.

Scope: only src/main/java/edu/cmu/cs214/scheduling/workflow/. New classes go there. Don’t touch src/test/, domain/, notify/, pricing/, reporting/, pom.xml, README.md or REFACTOR.md. Keep the strict < overlap in overlaps and the closed <= overlap in isTakenForSeries exactly as they are. Preserve the order of nextBookingId, nextSeriesId, save and publish, every message string, and every notification recipient. Run mvn -B test and show me the totals line. Don’t commit.
```

The directive said not to touch `REFACTOR.md`. This entry was added afterwards,
in a separate commit.

**The diff and the suite.** Commit `ccc6867` ("Replace conditional with
polymorphism in BookingWorkflow"), directly after `06c477b`. Show it with
`git show ccc6867`. It adds `BookingTypeHandler.java`, `RegularHandler.java`,
`RecurringHandler.java`, and `BlockedHandler.java`, and rewrites
`BookingWorkflow.java` to look handlers up in an `EnumMap<BookingType,
BookingTypeHandler>`. `mvn -B test`:
`Tests run: 36, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`.
`grep -n switch src/main/java/edu/cmu/cs214/scheduling/workflow/*.java`
returns nothing.

**What did NOT change.**

- *Files.* `git show --stat ccc6867` lists only the five files under
  `workflow/`. `git status` before the commit showed nothing changed in
  `src/test/`, `domain/`, `notify/`, `pricing/`, `reporting/`, `pom.xml`,
  `README.md`, or `REFACTOR.md`.
- *Strings.* The set of distinct string literals across all of `workflow/` is
  identical to `BookingWorkflow.java` at `06c477b`
  (`grep -o '"[^"]*"' | sort -u`, then `diff`).
- *Operators.* Still exactly two `<= 0` and three `< 0` across the package.
  The strict `overlaps` stays in `BookingWorkflow.java`, used by
  `RegularHandler` (room check and member check) and `BlockedHandler`. The
  closed check `isTakenForSeries` moved to `RecurringHandler.java`, its only
  caller, unchanged.
- *Call order and fallbacks.* `nextBookingId`, `nextSeriesId`, `save`, and
  `publish` happen in the same order inside each handler, and `cancel` still
  looks up the room name before choosing a handler. A missing handler returns
  the old `default` values ("unsupported booking type ...", `false`, `0.0`,
  `"Booking #<id> in <room>"`). This can't happen today, since all three types
  are registered and `BookingRequest` and `Booking` both reject a null type.
- *Structure that did change.* `overlaps`, `rejectMemberOrCapacity`,
  `recipientFor`, and `FACILITIES_CONTACT` went from `private` to
  package-private `static` on `BookingWorkflow` so the handlers can share them.
  `MAX_SERIES_WEEKS` moved to `RecurringHandler`. The Extract Method notes
  above describe where things lived at `e767d85`.

**One thing I looked at twice.** The recurring `<=`. After the move, the
closed-overlap check is at `RecurringHandler.java:115`, and lines 117–118 still
read `existing.getStart().compareTo(slot.end()) <= 0 &&
slot.start().compareTo(existing.getEnd()) <= 0`. It's called only from
`RecurringHandler.submit` (line 54), and none of the three `overlaps` call
sites ended up in the series path. The pin
`recurringSubmitSkipsAWeekThatStartsWhenAnotherBookingEnds` still passes. I
didn't re-run the `<=`-to-`<` mutation against `ccc6867`; the mutation result
above was measured on `e767d85`.

### The closing explanation

**Refactor or regenerate?** Argue whether regenerating `BookingWorkflow` from scratch
would have been the better call, using the lecture's four questions (test
coverage, code age, spec quality, and reach). Be concrete about this codebase.

Refactoring was the better call. Taking the four questions in turn:

- *Test coverage.* Thin exactly where `BookingWorkflow` is subtle. The 18
  shipped tests in `BookingWorkflowTest` cover each booking type's happy path
  and one rejection, but miss at least four decisions the code makes. These
  are: the RECURRING `<=` boundary (my pin); a series never checking the
  member's other bookings; `cancel` on one occurrence releasing that week and
  every later one but no earlier ones (`recurringCancelReleasesTheOccurrence`
  cancels the last week, so it can't tell); and an all-skipped series still
  coming back `isAccepted()`. The mutation check showed what that means: a
  regeneration could flip the boundary with all 35 shipped tests green.
  Refactoring under one pin is safe. Regenerating under that coverage is not.
- *Code age.* The repo has one commit, "Initial commit", so there is no history
  of why the series check uses `<=` or why series skip the member check.
  Whether these are deliberate rules (a buffer between weekly meetings) or bugs
  is unknown. Old, unexplained behavior is the kind a regeneration erases
  without anyone noticing, and the kind a refactor keeps by default.
- *Spec quality.* The only spec is the README's one paragraph ("members book
  rooms one slot at a time or as a weekly series..."). It doesn't say whether
  touching slots conflict, what a partial series means, or what cancelling one
  occurrence does. A regeneration from that would have to invent those answers,
  and would probably pick one half-open overlap rule for every type, a
  member-conflict check for series too, and cancel-one-occurrence-only. Each of
  those is a behavior change.
- *Reach.* Nothing in `src/main` calls `BookingWorkflow` directly, but
  everything it writes into `BookingStore` is read by `ReportService`.
  `occupancyFor` counts and sums the minutes of `activeInRoom`, and
  `totalRevenue` / `revenueForMember` price every live non-BLOCKED booking.
  So each week a regenerated series books instead of skipping changes
  occupancy, minutes, and revenue numbers. The notification subjects and
  bodies are exact strings in the `Outbox`, and a regeneration could easily
  reword them. The tests only count messages, so that change wouldn't show either.

Against that, the refactors' cost was small. Everything stayed inside
`workflow/`: `BookingWorkflow.java` plus four new package-private types
(`BookingTypeHandler`, `RegularHandler`, `RecurringHandler`, `BlockedHandler`).
Every string and operator was preserved and checked, and the pin still passes
on the one place a merge would have been tempting (`isTakenForSeries`, now in
`RecurringHandler`).

**What would flip your answer.** A condition about the artifact, not a feeling.

If `BookingWorkflowTest` pinned every decision listed above: the overlap
boundary for each booking type, member-conflict checking for series, which
occurrences `cancel` releases, the acceptance of an all-skipped series, and
the exact notification text. Then any difference in a regenerated class would
show up as a red test, and regenerating would be a reasonable, checkable call.
With today's suite, it isn't.

---

## Milestone 2: The pattern critique

Read `notify/`. It works and the outbox tests pass.

### The patterns present

List every design pattern you can name in that package. For each one, the class
or classes that carry it.

1. **Strategy**: `NotificationStrategy` (interface), `EmailNotificationStrategy`
   (the only implementation), held by `NotificationHub.strategy`.
2. **Observer**: `NotificationHub` is the subject (`subscribe`, `publish` loops
   over `subscribers`), `NotificationSubscriber` is the observer interface,
   `OutboxSubscriber` the only concrete observer.
3. **Factory** (a simple factory): `NotifierFactory.createStrategy()`, which
   always returns `new EmailNotificationStrategy()`.
4. **Singleton**: `NotifierFactory` (private constructor, lazy, `synchronized`
   `getInstance()`).
5. **Adapter**, loosely: `OutboxSubscriber` exists only to make `Outbox.append`
   fit the `NotificationSubscriber.onNotification` signature.

### The problem each one solves

For each pattern you listed, what would have to be true about the requirements
for that pattern to be the right call? One sentence each, not in terms of
"flexibility".

1. **Strategy**: the same message has to be rendered in more than one format
   (email, SMS, a facilities digest), and which one is chosen at runtime, per
   hub or per recipient.
2. **Observer**: more than one independent consumer has to receive every
   message (say, the outbox *and* a mail sender *and* an audit log), and which
   consumers exist is decided outside `NotificationHub`.
3. **Factory**: choosing the renderer takes real logic or configuration (read
   a setting, look up the member's preference) that callers shouldn't repeat.
4. **Singleton**: there must be exactly one of something process-wide because
   it owns shared state or a scarce resource (a connection pool, a config
   cache), and handing it around would be worse than global access.
5. **Adapter**: an existing class with a fixed interface (here `Outbox`) has
   to plug into a different interface it can't be changed to implement.

### Which of those problems exist here

For each pattern, does the problem it solves exist in this codebase? Point at
the code that settles it.

1. **Strategy: no.** `EmailNotificationStrategy` is the only implementation,
   and `NotificationHub` never takes one in: its constructor hard-wires
   `NotifierFactory.getInstance().createStrategy()`. Nothing can pass in a
   different renderer, so there is nothing to vary.
2. **Observer: no.** The only call to `subscribe` in `src/` is the hub's own
   constructor, `subscribe(new OutboxSubscriber(outbox))`. `BookingWorkflow`
   only calls `hub.publish`. There is exactly one consumer, and the hub picks it.
   The shipped test `hubDeliversToItsOneSubscriber` asserts that count is 1.
3. **Factory: no.** `createStrategy()` has no parameters and no branches; it is
   `return new EmailNotificationStrategy();`. It is called from one place.
4. **Singleton: no.** `NotifierFactory` has no fields at all, so there is no
   shared state for "exactly one" to protect. Two instances would behave
   identically. The only thing that depends on there being one is the test
   `factoryHandsBackTheSameInstance`, which tests the pattern, not a behavior.
5. **Adapter: no.** `Outbox` is our own class in the same package. If the hub
   needs to append to it, it can call `outbox.append` directly.

### The simpler structure

**Your proposal.** What replaces `notify/`. Sketch the classes and the one
method that matters.

Three types instead of seven: `NotificationMessage` (unchanged), `Outbox`
(unchanged), and a concrete `NotificationHub` that renders and appends itself:

```java
public class NotificationHub {
    private final Outbox outbox;

    public NotificationHub() { this(new Outbox()); }
    public NotificationHub(Outbox outbox) { /* same null check */ this.outbox = outbox; }

    public void publish(NotificationMessage message) {
        outbox.append("To: " + message.recipient()
                + " | Subject: " + message.subject()
                + " | " + message.body());
    }

    public Outbox getOutbox() { return outbox; }
}
```

Deleted: `NotificationStrategy`, `EmailNotificationStrategy`,
`NotifierFactory`, `NotificationSubscriber`, `OutboxSubscriber`. Nothing
outside `notify/` changes: `BookingWorkflow` still calls `hub.publish(...)`,
and every test still builds `new NotificationHub()` and reads `getOutbox()`.

**What stays the same.** The tested behavior it must still produce, named
precisely enough that a reader can check it against the shipped tests.

- `NotificationHubTest.publishedMessageLandsInTheOutboxFullyRendered`: one
  `publish` adds exactly one entry, and `getOutbox().last()` is
  `"To: <recipient> | Subject: <subject> | <body>"`, separators exactly
  `" | "`.
- `NotificationHubTest.aConfirmationFromTheWorkflowReachesTheOutbox`: a
  REGULAR `submit` produces that exact string for the confirmation.
- The `hub.getOutbox().size()` counts in `BookingWorkflowTest`: one entry per
  `publish`, in order, and none for a rejection (e.g. 1 after
  `regularSubmitStoresAndNotifies`, 4 after
  `recurringSubmitBooksEveryWeekOfAnOpenSeries`, 2 after
  `regularCancelReleasesTheSlotAndNotifies`, 0 after
  `submitRejectsAnUnknownRoom`).
- `NotificationMessage`'s constructor still rejects a blank recipient and a
  null subject, and `NotificationHub(Outbox)` still rejects null.

Two shipped tests don't survive this, and that is the point of the critique:
`hubDeliversToItsOneSubscriber` (asserts `subscriberCount() == 1`) and
`factoryHandsBackTheSameInstance` (asserts `getInstance()` returns the same
object). They pin the structure, not anything a user of the hub can observe,
so the proposal deletes them along with the classes they test. Since milestone
2 is a written critique, nothing is coded and the shipped 35 stay green.

**What you would keep, if anything.** If you would keep one interface, say
which and why. "None of it" is a fine answer if you can defend it.

None of the interfaces. Each has exactly one implementation, and no caller,
test, or configuration ever picks a different one. If a second renderer or a
second consumer shows up, extracting an interface from a concrete
`NotificationHub` is a small, mechanical change at that point. It's the same
change whether we do it now or later, and doing it later means we know what
the second implementation actually needs. The two classes I keep,
`NotificationMessage` and `Outbox`, aren't pattern layers: one is the data
`BookingWorkflow` builds, the other is what every test reads.

### What would bring each layer back

For at least two of the layers you would remove, what requirement, if it
arrived next sprint, would make that layer the right structure? Be specific
about the requirement, not about the pattern.

- **Observer (`NotificationSubscriber`, `subscribe`).** "Every confirmation
  and cancellation must also be written to an append-only audit log for the
  facilities office, and the outbox stays as it is." Now two independent
  consumers need every message, and which ones are wired up depends on
  deployment (tests want only the outbox). A subscriber list on the hub is the
  right structure.
- **Strategy (`NotificationStrategy`).** "Members can choose SMS instead of
  email; SMS must fit in 160 characters with no `To:`/`Subject:` header." Now
  the same `NotificationMessage` renders two ways, chosen by the recipient's
  preference, and a renderer interface with `EmailRenderer` and `SmsRenderer`
  earns its place.
- **Factory (`NotifierFactory`).** That same SMS requirement, plus "the
  default format is set per deployment in a config file." Choosing the
  renderer now takes real logic (read config, check member preference), and
  putting it in one place stops `BookingWorkflow` and the hub from each
  repeating it.

The Singleton wouldn't come back for any of these: even a configured factory
can be built once at startup and passed to the hub.

**Misuse or anti-pattern?** Say which this is and why the distinction matters.

Misuse. Strategy, Observer, and Factory are each implemented correctly; they
are just applied to problems this codebase doesn't have (speculative
generality). An anti-pattern is a structure that causes harm even when the
problem is real. The Singleton is closest to that line, because it's global
state, but here it's harmless because `NotifierFactory` has no fields.
The distinction matters for what to do about it. A misused pattern should be
removed now and brought back, unchanged, when the requirement shows up, as in
the list above. An anti-pattern should be replaced by a different design even
when the requirement is real. Calling this an anti-pattern would wrongly
suggest that Observer or Strategy are bad, when the next sprint might need
exactly them.

---

## Milestone 3: The missing pattern

Read `pricing/`. Not coded, one sentence.

**The pattern.** Which one fits `PriceCalculator`, and the problem that makes
it fit. Name the problem.

Decorator: `price()` is a base hourly rate wrapped in a fixed, ordered stack
of adjustments (weekend surcharge, then long-booking discount, then tier
discount), each applied to the running price from the previous one, so the
problem is a growing ordered list of price adjustments hard-coded as
successive `if` blocks in one method.

**Would you apply it today?** Yes or no, one line, with the reason.

No: there are four fixed rules in one 20-line method, each pinned by a test in
`PriceCalculatorTest`, and no requirement asks to add, reorder, or switch rules
per room or promotion.
