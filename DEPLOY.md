# Deploying a free live demo

This guide puts the app on the internet for free, for **demos and project reviews**:

- **Web app:** [Render](https://render.com) free web service (runs the Dockerfile in this repository)
- **Database:** [Aiven](https://aiven.io) free MySQL

> **Honest limits of the free setup**
> - The site **goes to sleep after 15 minutes** without visitors. The next visit takes about a minute to wake it.
> - Render's free disk is **temporary**: uploaded files (photos, resumes, PDFs, student work) are erased when the
>   site sleeps, restarts or is redeployed. Accounts, marks, attendance and text data are kept (they are in MySQL).
> - Running **Java, C and C++ on the server is switched off** (it is unsafe on a public site and the host has no
>   compilers). **Python and SQL still work**, because they run in the visitor's browser.
> - Anyone with the link can use the demo logins. Do not enter real student data.
>
> For real use (real students, files that must be kept), use a small rented server instead. See the end of this file.

## Before you start

1. Make sure the code is on GitHub (it is, in your repository).
2. **Rename the repository to remove the trailing dot** (GitHub → repository **Settings → General → Repository name**),
   for example to `mbu-digital-library`. A name ending in a dot confuses some tools.

## Step 1: create the free database (Aiven)

1. Sign up at <https://aiven.io> and create a project.
2. **Create service → MySQL → Free plan** → pick a region → **Create**. Wait until it shows **Running**.
3. On the service's **Overview** page, note: **Host**, **Port**, **User** (usually `avnadmin`), **Password**,
   and the **Database name** (usually `defaultdb`).
4. Make up your connection address from those values:

   ```
   jdbc:mysql://HOST:PORT/defaultdb?sslMode=REQUIRED&serverTimezone=UTC
   ```

   (Replace `HOST` and `PORT`. Use the database name Aiven shows if it is not `defaultdb`.)

## Step 2: create the web app (Render)

1. Sign up at <https://render.com> (signing in with GitHub is easiest) and allow it to see your repository.
2. **New + → Blueprint** → choose your repository → Render reads `render.yaml` → **Apply**.
   *(Or **New + → Web Service**, choose the repository, **Language: Docker**, **Instance type: Free**, and set
   **Health Check Path** to `/login`.)*
3. When asked for the secret values, enter:

   | Name | Value |
   |------|-------|
   | `DB_URL` | the `jdbc:mysql://...` address from Step 1 |
   | `DB_USERNAME` | the Aiven user (usually `avnadmin`) |
   | `DB_PASSWORD` | the Aiven password |

   The blueprint already sets `APP_CODE_RUNNER_ENABLED=false` and `APP_SHOW_DEMO_LOGINS=true`.
4. Wait for the first build (several minutes). When the log says the service is **Live**, open the address
   ending in `.onrender.com`.

On first start the app creates all its tables in the empty database and adds the three demo accounts.

## Step 3: use it

Open the Render address and log in with the demo logins shown on the sign-in page
(`admin` / `Admin@123`, `faculty1` / `Faculty@123`, `student1` / `Student@123`).

## If something goes wrong

| What you see | What it means / what to do |
|---|---|
| Render log: `Access denied for user` or `Communications link failure` | `DB_URL`, `DB_USERNAME` or `DB_PASSWORD` is wrong. Fix it under **Environment** and redeploy. Check that the Aiven service is **Running** |
| Render: "No open ports detected" or the deploy times out | The free instance is slow to start Java. Click **Manual Deploy → Deploy latest commit** once more; if it keeps failing, the free plan is too small and a paid instance is needed |
| Site loads slowly after being idle | It was asleep. Wait about a minute |
| Photos or PDFs show as missing | Expected on the free plan (temporary disk). Upload them again |
| Login keeps returning to the sign-in page | Make sure you open the **https://** address, not http:// |

## Moving to real use

When this is more than a demo, change these settings (Render → **Environment**):

| Setting | Value | Why |
|---------|-------|-----|
| `APP_SEED_SAMPLE_ACCOUNTS` | `false` | the demo passwords are public |
| `APP_INITIAL_ADMIN_PASSWORD` | your own strong password | creates one `admin` account |
| `APP_SHOW_DEMO_LOGINS` | `false` | hide the demo box |

And host it somewhere with a **persistent disk** for uploads (a small rented server, or your college's server),
using the same `Dockerfile`. Keep `APP_CODE_RUNNER_ENABLED=false` unless the container is locked down.
