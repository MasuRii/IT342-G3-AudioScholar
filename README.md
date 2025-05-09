# 🎓 AudioScholar: Transforming Audio into Actionable Insights for Learners

AudioScholar is an intelligent, multi-user platform designed to enhance lecture note-taking and content comprehension for students. It records lecture audio and uses AI-driven summarization, cloud sync with **Nhost**, and video recommendation features to produce structured, actionable insights and personalized learning experiences — all accessible via web and mobile apps.

![CITU Logo](https://cit.edu/wp-content/uploads/2023/07/cit-logo.png)

---

## 🚀 Key Features

### 🔊 Lecture Recording & Summarization
- Record lectures via the mobile app.
- AI generates structured summaries from captured audio.
- Summaries are accessible across devices.

### 📤 Audio Upload
- Upload existing audio files through the web or mobile interface.
- Summaries auto-generate after processing.

### 📚 AI-Powered Learning Recommendations
- Automatically suggests relevant **YouTube** videos based on lecture content.

### 🔐 User Authentication & Account Management
- Register/Login via **Firebase Authentication** (Email/Password, Google OAuth, or GitHub OAuth).
- Manage profiles and change passwords securely.
- Feature access is controlled via a **freemium model**.

### ☁️ Cloud Synchronization
- Sync recordings, summaries, and associated files to **Nhost Storage** (audio files) and **Firebase Firestore** (metadata, summaries).
- Choose between manual or automatic syncing where applicable.
- Data is accessible across authorized devices.

### 📊 PowerPoint Integration
- Upload and associate **PowerPoint presentations** with lecture recordings.
- Enhances summarization and context for lectures.

### 🌐 Web Interface
- View uploaded recordings and summaries.
- Upload audio files for summarization.
- Access AI-generated learning resources.

---

## ⚙️ Setup Instructions

### 📌 Prerequisites
Ensure the following tools are installed on your system:

- **Java Development Kit (JDK) 21**
- **Node.js** (v16+)
- **npm** or **yarn**
- **Git**
- **Maven** (for backend)
- **Android Studio** (Latest version)
- **Nhost Account** (for cloud file storage - [Nhost Cloud](https://nhost.io/))
- **Firebase Account** (for authentication and Firestore database - [Firebase Console](https://console.firebase.google.com/))

---

### 📁 Cloning the Repository
```bash
git clone https://github.com/IT342-G3-AudioScholar/AudioScholar.git
cd AudioScholar
```

---

### 🔧 Backend Setup (Spring Boot)
1. Navigate to the backend:
   ```bash
   cd backend/audioscholar
   ```
2. Set up environment variables and Firebase configuration:
   Create a `.env` file in the `backend/audioscholar` directory for secrets:
   ```dotenv
   # Nhost Storage Configuration
   NHOST_ADMIN_SECRET=your-nhost-admin-secret # Used by Spring Boot to access Nhost Storage

   # API Keys
   GEMINI_API_KEY=your-gemini-api-key
   YOUTUBE_API_KEY=your-youtube-api-key
   # Other secrets like JWT_SECRET, GOOGLE_CLIENT_ID/SECRET, GITHUB_CLIENT_ID/SECRET etc.
   # CONVERTAPI_SECRET=your-convertapi-secret
   # UPTIME_ROBOT_API=your-uptime-robot-api-key
   ```
   In `backend/audioscholar/src/main/resources/application.properties`:
   - Ensure `nhost.storage.url` is correctly set to your Nhost project's storage URL.
     ```properties
     # Example:
     # nhost.storage.url=https://your-nhost-project-subdomain.storage.your-nhost-region.nhost.run/v1/files
     ```
   - Configure Firebase Admin SDK by placing your `firebase-service-account.json` (downloaded from Firebase Console > Project settings > Service accounts) into the `backend/audioscholar/src/main/resources/` directory.
   - The `application.properties` should have the following configured:
     ```properties
     spring.cloud.gcp.project-id=your-firebase-project-id
     firebase.service-account.file=firebase-service-account.json
     # Ensure other Firebase related properties like firebase.database.url (if using Realtime DB alongside Firestore, though Firestore is primary)
     # and collection names are correctly set.
     ```
   *Note: Replace placeholders with your actual details.*
3. Run the backend:
   ```bash
   mvn spring-boot:run
   ```
   Or run `AudioscholarApplication.java` from your IDE. (Spring Boot version `3.4.5`)

---

### 💻 Web Frontend Setup (React + Vite)
1. Navigate to the web app:
   ```bash
   cd frontend_web/audioscholar-app
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Create a `.env` file in `frontend_web/audioscholar-app`:
   ```dotenv
   # Backend API URL
   VITE_API_URL=http://localhost:8080

   # Firebase Frontend Configuration (Find these in your Firebase project dashboard > Project settings > General > Your apps)
   VITE_FIREBASE_API_KEY=your-firebase-api-key
   VITE_FIREBASE_AUTH_DOMAIN=your-firebase-auth-domain
   VITE_FIREBASE_PROJECT_ID=your-firebase-project-id
   VITE_FIREBASE_STORAGE_BUCKET=your-firebase-storage-bucket # Optional, if using Firebase Storage directly from frontend
   VITE_FIREBASE_MESSAGING_SENDER_ID=your-firebase-messaging-sender-id
   VITE_FIREBASE_APP_ID=your-firebase-app-id
   # VITE_FIREBASE_MEASUREMENT_ID=your-firebase-measurement-id # Optional
   ```
   *Note: Ensure your React application is configured to use these Firebase environment variables.*
4. Run the development server:
   ```bash
   npm run dev
   ```
   (Uses Vite `~6.3.4`, React `^19.0.0`)
   Open at: `http://localhost:3000` (or the port specified by Vite)

---

### 📱 Mobile Frontend Setup (Kotlin + Android)
1. Open Android Studio → "Open an Existing Project"
2. Navigate to:
   ```
   frontend_mobile/AudioScholar
   ```
3. Sync Gradle files (Android Studio will prompt).
4. **Configure Firebase:**
   - Download your `google-services.json` file from the Firebase Console (Project settings > General > Your apps > Android app).
   - Place the `google-services.json` file in the `frontend_mobile/AudioScholar/app/` directory.
5. **Configure API Base URL:**
   In `frontend_mobile/AudioScholar/local.properties` (create it if it doesn't exist in the project root or `app/` directory). This file is typically ignored by Git.
   ```properties
   # Backend API URL (Use your machine's local network IP, not localhost, if running on a physical device)
   API_BASE_URL=http://your-local-network-ip:8080
   ```
   *Note: Ensure your Android app (e.g., Ktor client configuration) reads this `API_BASE_URL`.*
6. Run on an emulator or physical device. Ensure the device can reach your `API_BASE_URL`.

---

## 🧭 Example Workflow

1. Register or log in using **Firebase Authentication** (Email/Password or OAuth).
2. Record a lecture using the mobile app. Audio is uploaded (typically via the backend) to **Nhost Storage**.
3. Wait for AI processing (backend fetches audio from Nhost, processes, stores results and metadata in **Firebase Firestore**).
4. View the summary on web or mobile under **My Lectures** (data fetched from your backend, which interacts with **Firebase Firestore** for metadata/summaries and Nhost for audio files).
5. Access recommended YouTube videos for deeper learning.

---

## 🧩 Dependencies

### Backend
- **Spring Boot 3.4.5**
- **Nhost Interaction:** Via REST APIs to Nhost Storage (using URL configured in `application.properties` and `NHOST_ADMIN_SECRET`).
- **Firebase Admin SDK:** For interacting with Firebase Firestore (database).
- See full list in [`pom.xml`](./backend/audioscholar/pom.xml)

### Web Frontend
- **React 19**
- **Vite ~6.3.4**
- **Firebase SDK** (`firebase` package) for authentication and Firestore database access.
- Interaction with Nhost Storage is likely handled via the backend.
- See full list in [`package.json`](./frontend_web/audioscholar-app/package.json)

### Mobile Frontend
- **Kotlin + Jetpack Compose**
- **AndroidX**
- **Firebase SDKs** (e.g., `firebase-auth-ktx`, potentially `firebase-firestore-ktx` or using the BOM for Firestore) for authentication and database access.
- **Ktor client:** For network requests to the backend. The backend then interacts with Nhost Storage.
- See configurations in [`build.gradle.kts`](./frontend_mobile/AudioScholar/build.gradle.kts) (app level) and ensure `google-services.json` is correctly set up.

---

## 🧪 Features Outside Initial Scope (Planned for Future Releases)

| Feature                              | Status                  |
| ------------------------------------ | ----------------------- |
| Real-time Transcription              | 🚫 Not yet implemented   |
| iOS Mobile Support                   | 🚫 Not yet supported     |
| Web Audio Recording                  | 🚫 Not yet supported     |
| Multi-language Support               | 🚫 English only for v1.0 |
| Background Recording (Free Users)    | 🚫 Not supported         |
| Recommendation Engine beyond YouTube | 🚫 Future feature        |
| Premium Subscription Management      | 🚫 Planned               |

---

## 🎨 Design & Documentation

- **Use Case & Activity Diagrams**: [View on Figma](https://www.figma.com/design/5cqAE14jvnfFDlKbqHObr7/AudioScholar?node-id=24-2315&t=su6Bkd3yHO2aCleY-1)

- **Mobile Wireframes**: [View on Figma](https://www.figma.com/design/5cqAE14jvnfFDlKbqHObr7/AudioScholar?node-id=0-1&t=31ZcynnCihbXU6I4-1)  
  
- **Web Wireframes**: [View on Figma](https://www.figma.com/design/5cqAE14jvnfFDlKbqHObr7/AudioScholar?node-id=8-2267&t=31ZcynnCihbXU6I4-1)  
  
- **Database Schema & ER Diagrams**: [View on Figma](https://www.figma.com/design/5cqAE14jvnfFDlKbqHObr7/AudioScholar?node-id=24-2315&t=31ZcynnCihbXU6I4-1)


---

## 👨‍💻 Developers

**Proponents:**
- Biacolo, Math Lee L.
- Terence, John Duterte
- Orlanes, John Nathan

**Adviser:**
- Frederick L. Revilleza

---

## 📬 Contact

For issues, suggestions, or collaboration inquiries, feel free to open an issue or contact the development team.

---

✅ *AudioScholar — Empowering learners through intelligent audio insights.*
