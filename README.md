
# 🎓 AudioScholar: Transforming Audio into Actionable Insights for Learners

AudioScholar is an intelligent, multi-user platform designed to enhance lecture note-taking and content comprehension for students. It records lecture audio and uses AI-driven summarization, cloud sync, and video recommendation features to produce structured, actionable insights and personalized learning experiences — all accessible via web and mobile apps.

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
- Register/Login via **Email/Password**, **Google OAuth**, or **GitHub OAuth**.
- Manage profiles and change passwords securely.
- Feature access is controlled via a **freemium model**.

### ☁️ Cloud Synchronization (Optional)
- Sync recordings and summaries to **Firebase Cloud Storage**.
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
- **Firebase Account** (for authentication and cloud storage)

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
   Create a `.env` file:
   ```
   FIREBASE_CONFIG_PATH=/path/to/firebase-config.json
   GEMINI_API_KEY=your-gemini-api-key
   YOUTUBE_API_KEY=your-youtube-api-key
   ```
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
   ```
   VITE_API_URL=http://localhost:8080
   ```
4. Run the development server:
   ```bash
   npm run dev
   ```
   Open at: `http://localhost:3000`

---

### 📱 Mobile Frontend Setup (Kotlin + Android)
1. Open Android Studio → "Open an Existing Project"
2. Navigate to:
   ```
   frontend_mobile/AudioScholar
   ```
3. Sync Gradle files (Android Studio will prompt).
4. Add `google-services.json` to the `app/` directory.
5. In `local.properties`, set:
   ```
   API_BASE_URL=http://your-local-ip:8080
   ```
6. Run on emulator or physical device.

---

## 🧪 Testing Credentials
Use the following Firebase test credentials (if available):
- **Email**: *[To be provided]*
- **Password**: *[To be provided]*

---

## 🧭 Example Workflow

1. Record a lecture using the mobile app.
2. Wait for AI to process and summarize (1–2 minutes).
3. View the summary on web or mobile under **My Lectures**.
4. Access recommended YouTube videos for deeper learning.

---

## 🧩 Dependencies

### Backend
- **Spring Boot 3.4.2**
- **Firebase Admin SDK 9.2.0**
- See full list in [`pom.xml`](./backend/audioscholar/pom.xml)

### Web Frontend
- **React 19**
- **Vite 6.2.0**
- See full list in [`package.json`](./frontend_web/audioscholar-app/package.json)

### Mobile Frontend
- **Kotlin + Jetpack Compose**
- **AndroidX**
- **Firebase SDKs**
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

✅ *AudioScholar — Empowering learners through intelligent audio insights.

