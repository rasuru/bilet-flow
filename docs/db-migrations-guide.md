# Database Migrations — Beginner Guide

## 1. What is a migration?

Your application code changes over time, and the database schema must change with it.

Suppose your persistence model initially has:

```java
private String title;
```

Later you add:

```java
private String description;
```

The Java code now expects a new database column.

A migration is the recorded database change that updates the schema so it matches the application.

Typical migrations:

- create a table;
- add or remove a column;
- add constraints;
- create indexes;
- rename database objects;
- update existing data.

The important idea is:

> **Application code and database schema evolve together.**

---

# 2. Why not change PostgreSQL manually?

You could manually run:

```sql
ALTER TABLE event ADD COLUMN description TEXT;
```

That fixes your local database.

But your teammates still have the old schema.

Now everyone has:

```text
same Git code
different databases
```

This is called schema drift.

Instead, database changes should be represented by Liquibase migrations committed to Git.

---

# 3. Our default workflow: generate migrations with JHipster

For normal schema changes, you should **not write Liquibase XML manually first**.

The normal workflow is:

```text
change the JPA persistence model
↓
compile the project
↓
generate a Liquibase diff
↓
review the generated migration
↓
include it in the changelog
↓
run and test the application
```

For our Gradle project, the important JHipster/Liquibase command is:

```bash
./gradlew liquibaseDiffChangelog -PrunList=diffLog
```

If needed, compile first:

```bash
./gradlew compile
./gradlew liquibaseDiffChangelog -PrunList=diffLog
```

JHipster compares the persistence model with the current database schema and generates a Liquibase changelog describing the difference.

The generated migration should normally appear under:

```text
src/main/resources/config/liquibase/changelog/
```

JHipster documents this Liquibase diff workflow for generated relational applications. 

---

# 4. Generated does not mean automatically correct

The generator understands structural differences.

It does not always understand your **intent**.

For example, suppose you rename:

```java
eventName
```

to:

```java
title
```

A diff tool may interpret this as:

```text
remove event_name
add title
```

when what you really intended was:

```text
rename event_name to title
```

Those are very different because removing and recreating a column can lose existing data.

So the rule is:

> **Generate first, review second.**

Do not blindly commit generated migrations.

---

# 5. When should you write or edit a migration manually?

Manual Liquibase work should be the exception.

You may need it when the generated diff does not express the intended change correctly.

Typical examples:

- renaming a column or table;
- migrating existing data;
- adding a `NOT NULL` field when rows already exist;
- introducing unusual indexes or constraints;
- changing data in a specific way;
- a generated migration would destroy data;
- Liquibase/JHipster cannot infer what you intended.

In those cases, modify the generated migration or create a focused manual one.

---

# 6. The most important rule

Once a migration has been shared with the team:

> **Do not edit it casually. Create another migration instead.**

For example:

```text
001_create_event
002_add_description
003_add_visibility
```

If migration `002` has already been executed by other developers and you later want to change the description column, create:

```text
004_change_description
```

Do not rewrite `002`.

Before a migration is merged or shared, changing it on your own branch is fine.

---

# 7. Why old migrations should not be edited

Liquibase records which changesets have already been executed.

It also stores a checksum for them.

If someone edits an already-executed migration, Liquibase can detect that the migration file no longer matches the database history.

This often produces a checksum validation error.

Usually the correct response is:

```text
restore the old migration
+
create a new migration
```

Do not make clearing checksums your normal solution.

---

# 8. Existing data matters

A migration can work on an empty database and still fail on a database with existing rows.

Suppose you already have:

```text
event

id | title
---+---------
1  | Concert
2  | Meetup
```

Now you add:

```text
visibility NOT NULL
```

The database needs to know what value old rows should receive.

A safe migration may need to do this:

```text
1. add visibility as nullable
2. populate old rows
3. add NOT NULL constraint
```

This is one of the cases where a generated migration may need manual adjustment.

For BiletFlow, local database resets are acceptable because this is a short university project, but developers should still understand how existing data affects migrations.

---

# 9. JHipster/Liquibase files

The important directory is normally:

```text
src/main/resources/config/liquibase/
```

You will typically see:

```text
liquibase/
├── master.xml
└── changelog/
```

Generated migrations go under:

```text
config/liquibase/changelog/
```

For example:

```text
20260907153000_added_event_visibility.xml
```

The main Liquibase changelog is:

```text
master.xml
```

The generated migration must be included according to the convention already used by the project.

A migration file that exists but is not included will not run.

---

# 10. What a generated migration looks like

You normally do not need to write this from scratch, but you should be able to read it.

For example:

```xml
<changeSet id="20260907153000-1" author="developer">
    <addColumn tableName="event">
        <column name="subtitle" type="varchar(255)"/>
    </addColumn>
</changeSet>
```

This means:

```text
add a column called subtitle
to the event table
with varchar(255) type
```

You should understand what a generated migration will do before committing it.

---

# 11. Code and migration belong in the same PR

Suppose you add:

```java
private Instant salesStart;
```

The same PR should contain:

```text
persistence/model change
+
generated Liquibase migration
+
relevant tests
```

Do not merge the Java change and plan to add the migration later.

A useful rule:

> **If the application expects the schema to change, the migration belongs in the same PR.**

---

# 12. Constraints are migrations too

Database constraints are part of the schema.

Examples:

```text
PRIMARY KEY
FOREIGN KEY
UNIQUE
NOT NULL
INDEX
```

If your persistence change introduces one, make sure the generated migration represents it correctly.

This is especially important in Ticket Inventory, where database consistency matters for inventory and seat holds.

Do not assume application-level validation always replaces database constraints.

---

# 13. Normal team workflow

When your feature changes persistence:

1. Pull the latest `main`.
2. Create your feature branch.
3. Change the JPA/persistence model.
4. Compile the project.
5. Run:

```bash
./gradlew liquibaseDiffChangelog -PrunList=diffLog
```

6. Open the generated migration.
7. Review what it does.
8. Adjust it if the generated diff does not match your intent.
9. Make sure it is included by the main Liquibase changelog.
10. Start the application and verify Liquibase runs successfully.
11. Run relevant tests.
12. If practical, test with a fresh database.
13. Commit code and migration together.
14. Open the PR.

---

# 14. Test migrations

Two situations matter.

## Fresh database

```text
empty database
↓
all migrations
↓
current schema
```

This checks that the complete migration history works.

## Existing database

```text
old schema + existing rows
↓
new migration
↓
current schema
```

This catches problems such as:

```text
NOT NULL failures
duplicate values before UNIQUE
foreign-key violations
bad data transformations
```

If your application only works after deleting the database, check whether the migration is actually correct.

---

# 15. Local database resets

For BiletFlow, resetting a disposable local database is fine when useful.

For example:

```text
delete local database/container volume
↓
start with an empty database
↓
run application
↓
Liquibase applies all migrations
```

This is useful for checking migration history from scratch.

But:

> **A reset is not a replacement for creating migrations.**

The repository should still contain the migration needed to reproduce the current schema.

---

# 16. Common mistakes

### Changing Java but not generating a migration

Result:

```text
application expects column
database does not have column
```

### Changing PostgreSQL manually

Result:

```text
works on my machine
```

but not for teammates.

### Blindly trusting the generated diff

The generator sees structural differences, not business intent.

Always review it.

### Editing an old shared migration

This can cause Liquibase checksum errors and inconsistent histories.

Create another migration instead.

### Forgetting `master.xml`

The migration exists, but Liquibase never executes it.

### Only testing a fresh database

A migration can work on an empty database and fail on existing rows.

---

# 17. When Liquibase fails

Do not immediately:

```text
delete migrations
edit DATABASECHANGELOG manually
clear checksums
disable Liquibase
enable Hibernate ddl-auto=update
```

First understand the error.

### Checksum error

Ask:

> Did someone edit an already-executed migration?

Usually restore the original migration and add a new one.

### Column does not exist

Check:

```text
Did I generate the migration?
Is it included?
Did Liquibase run?
Am I connected to the right database?
```

### Table already exists

Possible causes:

```text
database was changed manually
two migrations create the same table
migration history and schema are out of sync
```

---

# 18. Checklist before pushing

- [ ] Did my persistence model change?
- [ ] Did I run the Liquibase diff generator?
- [ ] Did I review the generated migration?
- [ ] Does it match what I intended?
- [ ] Does it preserve existing data where necessary?
- [ ] Is it included by the main changelog?
- [ ] Did I avoid modifying an already-shared migration?
- [ ] Did I run the application and let Liquibase execute it?
- [ ] Did I run relevant tests?
- [ ] Are the code and migration in the same PR?

---

# 19. What to remember

If you remember only five things:

1. **Change the persistence model first, then generate the migration with JHipster/Liquibase.**
2. **Always review the generated migration before committing it.**
3. **Code and migration belong in the same PR.**
4. **Do not edit migrations that have already been shared; create another one.**
5. **Use manual Liquibase only when the generated migration cannot correctly express your intent.**

The normal workflow is:

```text
change JPA model
↓
generate Liquibase diff
↓
review migration
↓
run/test
↓
commit together
```

That is the migration workflow you should follow for most BiletFlow backend changes.