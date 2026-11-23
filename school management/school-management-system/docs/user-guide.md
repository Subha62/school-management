# School Management System: User Guide

This guide explains how to use every feature of the application, organised by who you are: **administrator**,
**teacher** or **student**. Start with [Getting started](#1-getting-started), then jump to your role.

- [1. Getting started](#1-getting-started)
- [2. Features shared by everyone](#2-features-shared-by-everyone)
- [3. Administrator guide](#3-administrator-guide)
- [4. Teacher guide](#4-teacher-guide)
- [5. Student guide](#5-student-guide)
- [6. Rules and limits at a glance](#6-rules-and-limits-at-a-glance)
- [7. Troubleshooting and FAQ](#7-troubleshooting-and-faq)

---

## 1. Getting started

### 1.1 Opening the application

Open the web address your school gives you (when running locally: <http://localhost:5173>). The application works in
any current browser on a computer, tablet or phone. On a small screen the left menu collapses behind the
**menu button** (three lines) at the top left.

### 1.2 Signing in

1. Open the **Sign in** page.
2. Type your **Email or ID**. Students use their admission number (for example `S-0001`), teachers use their
   employee ID (for example `T-0001`), administrators use their email address. Your email also works for all roles.
3. Type your **Password** (use the eye icon to show or hide it) and press **Sign in**.

You land on your own dashboard. Administrators go to `/admin`, teachers to `/teacher` and students to `/student`.
You cannot open pages that belong to another role; you will see an "Unauthorized" page instead.

> **Your first password** is set by an administrator when your account is created. Change it the first time you
> sign in (see [Changing your password](#23-changing-your-password)).

### 1.3 Why sign-in can fail

For safety, the error message is always the same, whatever the reason. Possible reasons:

- Wrong ID or password.
- **5 wrong attempts in a row lock the account for 15 minutes.** Even the correct password is refused while it is
  locked. An administrator can unlock it immediately (see [Users](#32-users)).
- The account is suspended, still pending or archived. Ask an administrator.

### 1.4 Signing out and staying signed in

Use the **account menu** (the person icon at the top right) and choose **Sign out**. Your session also expires on its
own after a period of inactivity; just sign in again. Always sign out on a shared computer.

### 1.5 Demo accounts (local/demo installs only)

| Role | Sign in with | Password |
|---|---|---|
| Administrator | `admin@demo.school` | `Demo@Pass2026!` |
| Teacher | `teacher@demo.school` or `T-0001` | `Demo@Pass2026!` |
| Student | `student@demo.school` or `S-0001` | `Demo@Pass2026!` |

These accounts exist only in development installs. A real school must never use them.

---

## 2. Features shared by everyone

### 2.1 Navigation

The **left menu** lists the pages available to your role, grouped under headings such as *People*, *Academics* and
*Finance*. The current page is highlighted. Items marked **Coming soon** are planned and not clickable yet.
The top bar shows the school name, your role and the **account menu**.

Keyboard users can press **Tab** once on any page and choose **Skip to main content** to jump past the menu.

### 2.2 Profile

Open **Profile** from the left menu or the account menu to see your own details. Details such as name or class are
maintained by administrators.

### 2.3 Changing your password

1. Account menu → **Change password**.
2. Enter your **current password** and the **new password** (twice).
3. Save. All your other signed-in devices are signed out and you stay signed in here.

**Password rules:** 10 to 72 characters, with at least one uppercase letter, one lowercase letter, one digit and one
special character. You cannot reuse your current password.

### 2.4 Forgot your password

1. On the Sign in page choose **Forgot password**.
2. Enter your email address and submit. You always see the same confirmation message, whether or not the address is
   registered.
3. If the account is active, you receive an email with a link. The link works **once** and expires after
   **30 minutes**.
4. Open the link, choose a new password and sign in. All earlier sessions are ended.

> Students and teachers without a working email address should ask an administrator to set a new password.
> Password-reset emails need the school's mail server to be configured.

### 2.5 Announcements

Everyone has an **Announcements** page. It lists the notices addressed to you (see [audiences](#37-announcements)),
newest first.

- Unread announcements show a **red counter** next to the menu item.
- Open an announcement to read it; it is then marked as read.
- Only published announcements within their active dates are shown.

### 2.6 Tables, search and paging

Most lists share the same behaviour:

- Use the **search box and filters** above the table, then the table updates.
- Lists show 20 rows per page; use the page controls at the bottom.
- The **⋮ (Actions)** button on a row opens that row's actions (for example Edit, Details, Delete).
- Required fields are validated as you save; read the message under the field and correct it.
- Deleting asks for confirmation. Many records cannot be deleted once other records depend on them; in that case
  mark them **inactive** instead.

---

## 3. Administrator guide

Administrators configure the school, manage people and money, publish results and review the audit trail.

### 3.1 Dashboard

**Admin dashboard** gives a school-wide overview:

- **People and classes:** active students, teachers and classes in the current academic year.
- **Today's attendance:** how many registers have been finalised and how many are still open, with a link to attendance reports.
- **Fee snapshot:** billed, collected, outstanding and overdue amounts, with a link to student fees.
- **Exams in progress:** exams open for marks, awaiting publication and already published.
- **Live announcements:** how many are visible now, with a link to manage them.
- **Recent activity:** the latest audit entries.
- **Backend status:** confirms the system is healthy.

If a card cannot load you will see "Unable to load the dashboard" with a **Retry** button.

### 3.2 Users

**People → Users** lists every sign-in account (administrators, teachers and students).

- **Search and filter** by name, ID, role or status.
- **Create an administrator:** choose **New administrator**, fill in name, email and an initial password, and give
  the password to the person securely.
- **Change status** (Actions menu): *Active*, *Suspended* or *Archived*. A suspended or archived person cannot sign
  in, and this takes effect immediately, even if they are signed in at that moment.
- **Unlock** an account that was locked by repeated failed sign-ins.

Account states: **Pending** (created, not yet active), **Active**, **Locked** (temporary, after failed sign-ins),
**Suspended**, **Archived**.

### 3.3 Students

**People → Students** manages student records.

1. **New student:** enter the admission number (this becomes the student's sign-in ID), the initial password,
   first and last name, email, phone, date of birth, gender, admission date, blood group (optional), address, and an
   emergency contact name and phone. The date of birth must be in the past.
2. Save. A sign-in account is created together with the record.
3. Open a student to see the **profile**, their **enrollments** (class history) and **guardians**.
4. **Edit** to correct details or change the student's status: *Active*, *Inactive*, *Withdrawn* or *Graduated*.

> If two people edit the same record at once, the second save is refused with a message that the record changed.
> Reload and apply your edit again.

### 3.4 Teachers

**People → Teachers** works like Students: create a teacher with employee ID (the sign-in ID), initial password and
contact details; edit later; and set the employment status (*Active*, *On leave*, *Resigned*, *Retired*).

### 3.5 Guardians

**People → Guardians** stores parents or guardians and links them to students.

- Create a guardian with contact details, then edit or delete as needed.
- From a **student's profile**, add a guardian link, set the **relationship** and mark who is the primary contact,
  edit a link or remove it.
- A student can have several guardians, and a guardian several students (siblings).
- Guardians are contacts only. They do **not** have a sign-in account.

### 3.6 Setting up the academic structure

Do this once per school year, in this order, because each step uses the one before it.

| Step | Menu | What to do |
|---|---|---|
| 1 | **Academic years** | Create the year (for example 2026-2027) and **Activate** it. Only one year is active. **Close** a finished year. |
| 2 | **Grades** | Create grade levels (for example Grade 1 … Grade 12). |
| 3 | **Sections** | Create classes (sections) under a grade for the year, for example "Grade 5 - A", and assign a **class teacher**. |
| 4 | **Subjects** | Create the subject catalogue (name and code). |
| 5 | **Teacher assignments** | Link subject + section + teacher: who teaches what in which class. |
| 6 | **Enrollments** | Place students in sections (see below). |
| 7 | **Timetable** | Build the weekly timetable (see [3.8](#38-timetable)). |

### 3.7 Enrollments and promotion

**Enrollments** records which section a student belongs to for a year.

- **Enroll** a student in a section for the active year.
- **Transfer** a student to another section.
- **Withdraw** a student who leaves.
- **Complete** an enrollment at the end of the year.

Enrollment states: *Active*, *Transferred*, *Promoted*, *Withdrawn*, *Completed*. History is kept; nothing is
overwritten.

**Student promotion** moves students forward at year end.

1. Choose the **From academic year** and the **To academic year**.
2. Tick the students you want to promote (or **Select all on this page**). Students you do not tick stay where they
   are.
3. Choose a **Target section** for every selected student.
4. Press **Promote N selected** and confirm. A summary lists anyone who was **not promoted** and why.

Promotion is recorded in the audit log.

### 3.8 Timetable

**Operations → Timetable** builds the weekly schedule.

1. Choose **New entry**.
2. Select the **section**, **subject**, **day**, **period number** (1 to 20), **start and end time**, and optionally
   a **room**. The teacher defaults to the one assigned to that subject in that section, or you can choose another.
3. Optionally limit the entry with **effective from / to** dates; leave empty to cover the whole year.
4. Save. The system refuses clashes, such as a teacher or a section booked twice at the same time.

View the timetable **by section** or **by teacher**. Entries can be edited, set inactive or deleted.

### 3.9 Attendance reports and corrections

**Operations → Attendance reports** is read-mostly for administrators, because teachers take attendance.

- **Sessions:** browse attendance registers by date, section and type (*Daily* or *Subject*), and open one to see
  each student's mark.
- **Monthly report:** attendance per student for a month.
- **Below threshold:** students whose attendance is under the limit (75% by default).
- **Corrections:** once a teacher has finalised a register, only an administrator can change it. Open the session,
  choose a student, set the new status and enter a **reason** (5 to 500 characters). Every correction is kept in the
  history and in the audit log.

Attendance statuses: **Present, Absent, Late, Half day, Excused**.

### 3.10 Announcements

**Operations → Announcements** is where notices are written and managed.

1. **New announcement:** enter a title and message and choose the **audience**:
   *Everyone*, *Admins*, *Teachers*, *Students*, or a specific **Section**.
2. Optionally set **Publish at** and **Expires at** to schedule it.
3. Save it as a **Draft**, then **Publish** when ready. Later you can **Archive** it.
4. People see it only while it is published and within its dates.

### 3.11 Examinations

An exam goes through a fixed life cycle, and each step unlocks the next:

**Draft → Open for marks → Locked → Published**

**First, set the grade scale: Examinations → Grade rules.** Add grade bands (for example A+ = 90 to 100, A = 80 to
89.99, …). The bands must cover **0 to 100% with no gaps and no overlaps**, otherwise exams cannot be locked or
published. Changes to the scale are recorded in the audit log.

**Then create the exam: Examinations → Exams → New exam.**

1. Give it a **name** (unique per grade per year), pick the **grade**, and set start and end dates. Exams can only be
   created in the **active** academic year.
2. Add the **subjects**: for each, set **maximum marks**, **pass marks** and optionally an exam date.
3. A draft exam can still be edited or deleted. Other states cannot.

**Run the exam from its detail page:**

| Action | What it does |
|---|---|
| **Open for marks** | Teachers can now enter marks and comments. |
| **Progress** | Shows, per subject and class, how many marks are entered out of how many are expected. |
| **Lock** | Stops marking. Only possible from *Open for marks*. |
| **Reopen** | Goes back to *Open for marks* if corrections are needed (before publishing). |
| **Publish** | Locks the results in permanently and makes them visible to students. Needs a locked exam with **all marks entered**. |

**Examinations → Results** previews the calculated results (total, percentage, grade, pass or fail, comments) for a
locked or published exam and lets you open a student's **report card** and print it.

> Publication is final: published results cannot be corrected afterwards. Check the preview first.

### 3.12 Fees and payments

Money is handled in four steps.

**1. Fee categories** (Finance → Fee categories): the kinds of fee, for example Tuition, Transport, Library. A
category can be set inactive.

**2. Fee structures** (Finance → Fee structures): an amount and due date for one category in one grade for one year.

- Choose year, grade and category; enter the **amount** (more than 0, two decimals) and the **due date**.
- Press **Generate** to bill every student enrolled in that grade. It is safe to press again later: students who are
  already billed are skipped, and new students are added.
- Once fees are generated from a structure, only its description and active flag can change.

**3. Student fees** (Finance → Student fees): each student's individual charges. Filter by student, class or status.
Open one to see the **payment history** and take actions:

- **Record payment:** amount (cannot exceed the balance), **method** (Cash, Cheque, Bank transfer, UPI, Card,
  Other), optional reference number and the payment date. A **receipt number** is issued automatically.
- **Waive:** forgive the fee. A **reason** (5 to 300 characters) is required. A waiver can be **removed** (unwaived).

**4. Payments** (Finance → Payments): every payment received, with its receipt.

- Open a payment to **print the receipt** (use your browser's print dialog or Save as PDF).
- **Reverse** a payment recorded by mistake. A **reason** (5 to 300 characters) is required. The original payment is
  kept and marked reversed; the fee balance is restored.

**Fee statuses** are worked out automatically from the amounts and dates and are never typed in:

| Status | Meaning |
|---|---|
| Not due | Nothing paid yet and the due date is more than 14 days away |
| Due | Nothing paid yet and the due date is within the next 14 days |
| Partially paid | Some money received, balance remains |
| Paid | Fully paid |
| Overdue | Past the due date with a balance |
| Waived | Forgiven by an administrator |

**Reports:** the **Student fees** page shows a **summary** (billed, collected, outstanding, overdue, waived and totals
per category) that you can narrow by year and grade. The **Payments** page shows **Collections**, the money received
in a date range, split by payment method.

### 3.13 Audit logs

**System → Audit logs** is the permanent record of important actions, such as sign-ins, account changes, payments,
reversals, attendance corrections, exam locking and publishing, grade-scale changes and promotions.

- **Filter** by action, entity type, date range ("From" / "To") or text in the summary.
- Open a row's **Details** to see who did it, when, from which IP address, and a **Before / After** comparison of
  changed values. Secrets such as passwords show as `[REDACTED]`.
- The log is **read-only**. Nobody can edit or delete entries through the application.

---

## 4. Teacher guide

A teacher sees only their own classes and subjects.

### 4.1 Dashboard

**Teacher dashboard** shows "Today's timetable", the number of classes you teach, submissions **awaiting review**,
**marks still missing** for open exams, the **registers you still need to take** and your unread announcements. Use
the links on the cards to jump straight to the work.

### 4.2 My timetable

**My timetable** shows your week: day, period, time, subject, class and room.

### 4.3 Taking attendance

**Attendance** lists the classes you can take attendance for. You can only take attendance for:

- your own class, as the **class teacher** (a *Daily* register), and
- a subject you teach (a *Subject* register for a particular period).

Steps:

1. Choose **New register**, pick the class, the **date** and, for a subject register, the **period**.
2. The student list appears. Mark each student **Present, Absent, Late, Half day** or **Excused**, with an
   optional remark (200 characters).
3. **Save** as often as you like while the register is **open**.
4. Press **Finalize** when done. Every enrolled student must be marked first. A finalised register is locked and
   only an administrator can correct it.

You can take attendance for today and up to **7 days back** (the school can change this limit). A register that is
still **open** can be deleted.

**Monthly summary:** pick a class and a month to see each student's attendance.

### 4.4 Assignments

**Assignments** is where you set homework and classwork.

**Create an assignment**

1. Choose **New assignment**.
2. Pick the **class** and **subject** (only ones you teach are offered).
3. Enter a **title** (up to 200 characters), **instructions** (up to 5,000 characters), the **due date and time**,
   and the **maximum marks** (0.01 to 1000).
4. Decide whether **late submissions are allowed**.
5. Optionally **attach files** (up to 10 files).
6. Save. It is a **Draft** that students cannot see yet.

**Life cycle:** Draft → **Publish** (students can see and submit) → **Close** (no more submissions; **Reopen** to
allow them again) → **Archive** (hidden away). A draft can be edited or deleted.

### 4.5 Reviewing submissions

Open an assignment to see its submissions and their status. The
**Submission review** page lists work waiting for you across all assignments.

For each submission you can download the student's files, then record a review:

- **Mark as reviewed** and give **marks** (not above the maximum) and written **feedback** (up to 2,000 characters), or
- **Request resubmission**, so the student can send a new version.

Submission states: **Submitted, Late, Reviewed, Resubmission requested**.

### 4.6 Exams and marks

**Exams and marks** lists the exams that are **open for marks** for the subjects you teach.

**Entering marks**

1. Choose the exam, subject and class.
2. Type each student's marks, or tick **Absent**. Marks cannot exceed the maximum, and an absent student cannot have
   marks.
3. **Save.** You can save partially and come back.

Marks can only be entered while the exam is **Open for marks**. After an administrator locks it, they are read-only.

**Comments (class teachers only):** the class teacher can write a comment for each student, which appears on the
report card, while the exam is open for marks.

### 4.7 Announcements

You see announcements addressed to everyone, to teachers, or to your sections. Teachers read announcements;
administrators create them.

### 4.8 Coming soon

**My classes** and **Student list** are shown in the menu as *Coming soon*.

---

## 5. Student guide

A student sees only their own information.

### 5.1 Dashboard

**Student dashboard** shows your class, "Today's timetable", your **attendance percentage this month** (blank until a
register has been finalised), **upcoming work** (including resubmissions requested), your **latest published
result**, your **fee snapshot** (outstanding amount, overdue fees and the next due date) and unread announcements.

### 5.2 My timetable

Your weekly timetable: day, period, time, subject, teacher and room.

### 5.3 Attendance

**My attendance** shows one month at a time (use the arrows or the **Month** field). It counts only **finalised**
registers, and **excused days are not counted against you**. You see:

- **Daily attendance** and **Subject-wise attendance** percentages, with the number of present, late, half-day and
  absent marks. A **"Below 75%"** warning appears if you fall under the school's limit.
- An **Attendance by day** table.

If you believe a mark is wrong, tell your class teacher; only an administrator can correct a finalised register.

### 5.4 Assignments and submissions

**Assignments** lists work set for your class. Open one to read the instructions, see the due date, maximum marks and
whether late work is accepted, and **download** any files your teacher attached.

**To submit**

1. Open the assignment.
2. Type your text in **Your answer** and/or **attach up to 5 files** under **Your files**.
3. Press **Submit**. Work handed in after the deadline is marked **Late**; if late work is not allowed, the system
   refuses it.
4. To change it, open the assignment again, edit the answer, remove files with **Remove**, add new ones and press
   **Update submission**.

**File rules:** each file at most **10 MB**. Allowed types: **PDF, Word, Excel, PowerPoint, text, PNG and JPG**.
Other types are rejected.

After your teacher reviews it, the **Submissions** page shows the status, **marks** and **feedback**. If the teacher
requests a **resubmission**, open the assignment and send a new version.

### 5.5 Exams and results, report cards

**Exams and results** lists exams whose results have been **published**. Results do not appear before then.

Open one to see your **report card**: marks per subject, percentage, grade, pass or fail, overall result and your
class teacher's comment. **Report cards** lists all of yours. Use your browser's **Print** to print or save a PDF.

### 5.6 Fees

**Fees** shows what you owe and have paid.

- Each fee shows the amount, due date, amount paid, balance and **status** (see the [status table](#312-fees-and-payments)).
- Open a fee to see its **payments**. Choose a payment to view or print its **receipt**.
- Students view fees only. Payments are recorded by the school office.

### 5.7 Announcements

Notices for everyone, for students, or for your class. The red counter shows how many you have not yet read.

---

## 6. Rules and limits at a glance

| Topic | Rule |
|---|---|
| Password | 10 to 72 characters with upper case, lower case, digit and special character |
| Lock-out | 5 wrong passwords lock the account for 15 minutes |
| Password reset link | Single use, valid for 30 minutes |
| Page size | 20 rows by default, 100 at most |
| Attendance entry | Teachers: today and up to 7 days back. Corrections after finalising: administrators only |
| Attendance threshold | 75% by default |
| Attendance statuses | Present, Absent, Late, Half day, Excused |
| Upload size and types | 10 MB per file. PDF, Word, Excel, PowerPoint, text, PNG, JPG |
| Files per assignment / submission | 10 by the teacher, 5 by the student |
| Assignment marks | 0.01 to 1000, up to 2 decimals |
| Exam life cycle | Draft → Open for marks → Locked → Published (Reopen allowed before publishing) |
| Grade scale | Must cover 0 to 100% with no gaps or overlaps |
| Published results | Final, they cannot be changed |
| Payments | Cannot exceed the balance. Mistakes are reversed, never deleted. Reason needed for waive and reverse |
| Fee "Due soon" window | 14 days by default |
| Audit logs | Read-only and permanent |

---

## 7. Troubleshooting and FAQ

**I cannot sign in.** Check the ID and password (the password is case-sensitive). After 5 failures the account is
locked for 15 minutes, or an administrator can unlock it. If it persists, ask an administrator to check that the
account is *Active*.

**I do not get the password-reset email.** Check the spam folder and that you typed the registered address. Only
active accounts receive the email. If the school's mail server is not set up, ask an administrator to set the password.

**"Unauthorized" page.** You opened a page that belongs to another role. Use the menu to return to your pages.

**"This record was changed by someone else".** Another user saved first. Reload the page and repeat your change.

**I cannot delete something.** Other records depend on it. Mark it inactive or archive it instead.

**I cannot lock or publish an exam.** Check the message: the usual causes are missing marks, a grade scale with gaps
or overlaps, or classes with no subject teachers assigned.

**I cannot take attendance for a past day.** Teachers can go back 7 days. Ask an administrator to correct older
registers.

**A student does not appear in a class.** They need an *Active* enrollment in that section for the current year.

**A student cannot see results.** Results appear only after an administrator **publishes** the exam.

**The page shows "Unable to load".** Press **Retry**. If it continues, the server may be down, so tell an administrator.

**Printing.** Report cards and receipts are print-ready pages. Use the browser's Print command (Ctrl + P) and choose
*Save as PDF* if you want a file.

**Who can see my data?** Students see only their own records, teachers only their own classes and subjects, and
administrators see the whole school. Every sensitive change is recorded in the audit log.
