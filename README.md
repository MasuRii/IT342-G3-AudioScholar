# AudioScholar: Transforming Audio into Actionable Insights for Learners

  

![CITU Logo](https://cit.edu/wp-content/uploads/2023/07/cit-logo.png)

  

## 1. System Name

  

**AudioScholar**

  

## 2. Product Description

  

AudioScholar is an intelligent, multi-user platform designed to enhance the lecture note-taking process for students. It is a dual-platform solution comprising a mobile application for Android devices and a web interface accessible through standard web browsers.

  

The system's core functionality is to record lecture audio and leverage AI-driven summarization techniques to produce structured, actionable insights tailored for learners. AudioScholar goes beyond simple recording by offering personalized learning material recommendations, PowerPoint integration for enhanced summarization, and optional cloud synchronization.

  

**Key Objectives:**

  

*  **Capture Lectures:** Robust audio recording capabilities for both online and offline learning environments.

*  **Summarize Content:** AI-powered summarization using Google Gemini AI API to transform lengthy audio into digestible key points.

*  **Recommend Learning Materials:** Personalized recommendations of supplementary educational resources, initially focusing on YouTube.

*  **Provide Comprehensive Access:** Web interface for reviewing recordings, summaries, and recommendations on a larger screen.

  

AudioScholar is designed to be a standalone application, initially not integrated with Learning Management Systems (LMS), but future integration is considered. It operates on a freemium model, offering basic services for free and advanced features for premium subscribers.

  

## 3. List of Features

  

AudioScholar provides a comprehensive suite of features across its mobile and web platforms:

  

**Core Features:**

  

*  **Lecture Recording (Mobile):**

	- Start/Stop Lecture Recording: Capture high-quality audio lectures in real-time, both online and offline.

	* Background Recording (Premium Feature): Continue recording even when the app is in the background (for premium users).

*  **Audio Upload (Mobile & Web):**

	* Upload Pre-recorded Audio Files: Process existing audio lecture files from mobile or web.

*  **AI-Powered Summarization (Server-side):**

	* Automatic Audio Processing: Utilize Google Gemini AI API to process audio recordings.

	* Generate Lecture Summaries: Create structured and concise summaries of lecture content.

*  **Learning Material Recommendation (Server-side):**

	* Analyze Lecture Content: Extract keywords and key topics from lecture summaries using NLP.

	* Generate YouTube Recommendations: Suggest relevant educational YouTube videos based on lecture content.

*  **User Authentication & Account Management (Mobile & Server-side):**

	* User Registration: Create new accounts using email/password.

	* User Login: Secure login via Google OAuth 2.0, GitHub OAuth 2.0, or Email/Password.

	* Account Profile Management: View and edit user profile information.

	* Change Password (Email/Password Accounts): Securely update account passwords.

*  **Cloud Synchronization (Mobile & Server-side - Optional):**

	* Configure Cloud Sync Settings: Enable/disable cloud sync, set sync frequency, and choose data types to sync.

	* Manual/Automatic Cloud Sync: Synchronize recordings and summaries to Firebase Cloud Storage for data backup and cross-device access.

*  **PowerPoint Integration (Mobile & Server-side):**

	* Upload PowerPoint Presentations: Upload lecture slides to the mobile application.

	* Associate PowerPoint with Recording: Link uploaded PowerPoint presentations to specific lecture recordings for enhanced summarization.

*  **Web Interface (Web):**

	* View Recordings and Summaries: Access and review lecture recordings and AI-generated summaries.

	* Upload Audio Files: Upload pre-recorded audio files for processing via the web interface.

	* View Recommendations: Explore learning material recommendations for lectures.

*  **Freemium Model (Mobile & Server-side):**

	* Feature Access Control: Dynamically control feature access based on user login status and subscription level.

  

**Features Outside Initial Scope (Version 1.0):**

  

* Real-time Transcription

* iOS Mobile Platform Support

* Web Interface Audio Recording

* Multi-language Support (Initially English only)

* Background Recording for Free Logged-in Users

* Recommendation Engine Beyond YouTube (for Free Users)

* Premium Subscription Management

  

## 4. Links

  

*  **Diagrams (Use Case, Activity):** ![Link](https://www.figma.com/design/5cqAE14jvnfFDlKbqHObr7/AudioScholar?node-id=24-2315&t=su6Bkd3yHO2aCleY-1)

*  **Wireframes:** ![Link](https://www.figma.com/design/5cqAE14jvnfFDlKbqHObr7/AudioScholar?node-id=24-2315&t=su6Bkd3yHO2aCleY-1)

  

## 5. Developers Profile

  

**Proponent(s):**

  

* Biacolo, Math Lee L.

* Terence, John Duterte

* Orlanes, John Nathan

  

**Adviser:**

  

* Frederick L. Revilleza

  

## 6. Technology Stack

  

AudioScholar is built using a modern and robust technology stack:

  

*  **Mobile Application:**

	*  **Kotlin:** Native Android development language.

	*  **Firebase SDKs:** For Android integration with Firebase services.

*  **Web Interface:**

	*  **ReactJS:** JavaScript library for building interactive user interfaces.

*  **Server-side Application:**

	*  **Spring Boot (Java):** Framework for building robust and scalable server-side applications.

	*  **Firebase SDKs:** For Server-side integration with Firebase services.

*  **AI & APIs:**

	*  **Google Gemini AI API:** For audio processing and summarization.

	*  **YouTube Data API:** For learning material recommendations.

	*  **Google OAuth 2.0 & GitHub OAuth 2.0 APIs:** For federated login.

*  **Database & Cloud Services:**

	*  **Firebase:** Comprehensive mobile and web application development platform.

		*  **Firebase Authentication:** For user authentication and account management.

		*  **Firebase Firestore/Realtime Database:** For storing user data, recordings metadata, summaries, and recommendations.

		*  **Firebase Storage:** For optional cloud storage of audio recordings and summaries.

  

## 7. Getting Started

  

AudioScholar is designed for **college and university students** seeking to improve their lecture note-taking efficiency and study habits.

  

**Intended Users:**

  

* Undergraduate and graduate students across all disciplines.

* Students attending in-person and online lectures.

* Users comfortable with mobile applications and web browsers.

  

**User Roles & Access:**

  

*  **Free User (Unauthenticated):** Basic offline lecture recording and limited summarization.

*  **Logged-in Free User (Authenticated):** Access to core features including cloud sync (optional), basic recommendations, and limited web interface access.

*  **Premium User (Subscribed - Future):** Full access to all features, advanced summarization, expanded recommendations, priority processing, and potentially other premium features.
