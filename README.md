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
- Register/Login via **Email/Password**, **Google OAuth**, or **GitHub OAuth**
- Manage profiles and change passwords securely.
- Feature access is controlled via a **freemium model**.

### ☁️ Cloud Synchronization (Optional)
- Sync recordings and summaries to **Nhost Storage**.
- Choose between manual or automatic syncing.
- Configure data types and frequency of sync.

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

- **Java Development Kit (JDK) 17**
- **Node.js** (v16+)
- **npm** or **yarn**
- **Git**
- **Maven** (for backend)
- **Android Studio** (Latest version)
- **Nhost Account** (for authentication and cloud storage - [Nhost Cloud](https://nhost.io/))
- **Firebase Account** (Potentially needed *only* if specific Firebase services *other* than Auth/Storage are used, e.g., Cloud Messaging. Check project specifics.)

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
2. Set up environment variables:
   Create a `.env` file with your Nhost project details and API keys:
   ```dotenv
   # Nhost Configuration (Find these in your Nhost project dashboard)
   NHOST_SUBDOMAIN=your-nhost-subdomain
   NHOST_REGION=your-nhost-region
   NHOST_ADMIN_SECRET=your-nhost-admin-secret
   # NHOST_BACKEND_URL=https://<your-subdomain>.<your-region>.nhost.run (Often constructed or provided)

   # Other API Keys
   GEMINI_API_KEY=your-gemini-api-key
   YOUTUBE_API_KEY=your-youtube-api-key

   # Optional: If Firebase is still used for other services
   # FIREBASE_CONFIG_PATH=/path/to/firebase-config.json
   ```
   *Note: Replace placeholders with your actual Nhost project details.*
3. Run the backend:
   ```bash
   mvn spring-boot:run
   ```
   Or run `AudioscholarApplication.java` from your IDE.

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
3. Create a `.env` file:
   ```dotenv
   # Backend API URL
   VITE_API_URL=http://localhost:8080

   # Nhost Frontend Configuration (Find these in your Nhost project dashboard)
   VITE_NHOST_SUBDOMAIN=your-nhost-subdomain
   VITE_NHOST_REGION=your-nhost-region
   # VITE_NHOST_BACKEND_URL=https://<your-subdomain>.<your-region>.nhost.run (Often used directly by Nhost SDK)
   ```
   *Note: The Nhost React SDK typically uses subdomain and region to construct the necessary URLs.*
4. Run the development server:
   ```bash
   npm run dev
   ```
   Open at: `http://localhost:3000` (or the port specified by Vite)

---

### 📱 Mobile Frontend Setup (Kotlin + Android)
1. Open Android Studio → "Open an Existing Project"
2. Navigate to:
   ```
   frontend_mobile/AudioScholar
   ```
3. Sync Gradle files (Android Studio will prompt).
4. **Configure Nhost:** Add your Nhost project details to the `local.properties` file (create it if it doesn't exist in the project root or `app/` directory). This file is typically ignored by Git.
   ```properties
   # Nhost Configuration
   NHOST_SUBDOMAIN=your-nhost-subdomain
   NHOST_REGION=your-nhost-region

   # Backend API URL (Use your machine's local network IP, not localhost)
   API_BASE_URL=http://your-local-network-ip:8080
   ```
   *Note: Ensure the Nhost Kotlin SDK is configured in the project to read these properties.*
5. **Remove Firebase Config (if applicable):** If you previously had `google-services.json`, ensure it's removed from the `app/` directory if Firebase Auth/Storage are no longer used.
6. Run on an emulator or physical device. Ensure the device can reach your `API_BASE_URL`.

---

## 🧪 Testing Credentials
- Use test credentials configured within your **Nhost** project if available.
- Alternatively, register a new test user through the application's sign-up flow.
- **Email**: *[To be provided or use self-registered]*
- **Password**: *[To be provided or use self-registered]*

---

## 🧭 Example Workflow

1. Register or log in using Nhost authentication (Email/Password or OAuth).
2. Record a lecture using the mobile app. Audio is uploaded to **Nhost Storage**.
3. Wait for AI processing (backend fetches audio from Nhost, processes, stores results).
4. View the summary on web or mobile under **My Lectures** (data fetched from your backend, which interacts with Nhost).
5. Access recommended YouTube videos for deeper learning.

---

## 🧩 Dependencies

### Backend
- **Spring Boot 3.4.2**
- **Nhost Interaction:** Via REST APIs or potentially Nhost Java/Kotlin libraries (check `pom.xml`).
- See full list in [`pom.xml`](./backend/audioscholar/pom.xml)

### Web Frontend
- **React 19**
- **Vite 6.2.0**
- **Nhost React SDK** (`@nhost/react`, `@nhost/react-auth`, `@nhost/react-apollo` etc. - check `package.json`)
- See full list in [`package.json`](./frontend_web/audioscholar-app/package.json)

### Mobile Frontend
- **Kotlin + Jetpack Compose**
- **AndroidX**
- **Nhost Kotlin SDK** (Check `build.gradle.kts` for specific Nhost dependencies)
- See configurations in [`build.gradle.kts`](./frontend_mobile/AudioScholar/build.gradle.kts)

---

## 🧪 Features Outside Initial Scope (Planned for Future Releases)

| Feature | Status |
|--------|--------|
| Real-time Transcription | 🚫 Not yet implemented |
| iOS Mobile Support | 🚫 Not yet supported |
| Web Audio Recording | 🚫 Not yet supported |
| Multi-language Support | 🚫 English only for v1.0 |
| Background Recording (Free Users) | 🚫 Not supported |
| Recommendation Engine beyond YouTube | 🚫 Future feature |
| Premium Subscription Management | 🚫 Planned |

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
