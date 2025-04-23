// frontend_web/audioscholar-app/src/config/firebaseConfig.js

// Import the functions you need from the SDKs you need
import { initializeApp } from "firebase/app";
import { getAnalytics } from "firebase/analytics";
// Import getAuth to export the auth instance
import { getAuth } from "firebase/auth"; // <--- Add this import

// TODO: Add SDKs for Firebase products that you want to use
// https://firebase.google.com/docs/web/setup#available-libraries

// Your web app's Firebase configuration
// For Firebase JS SDK v7.20.0 and later, measurementId is optional
const firebaseConfig = {
  apiKey: "AIzaSyC-FRGSjiwzANRuSYxIY7Zm_1coY8viajc",
  authDomain: "audioscholar-39b22.firebaseapp.com",
  databaseURL: "https://audioscholar-39b22-default-rtdb.firebaseio.com",
  projectId: "audioscholar-39b22",
  storageBucket: "audioscholar-39b22.firebasestorage.app",
  messagingSenderId: "94663253748",
  appId: "1:94663253748:web:049c1293085af7caff48a1",
  measurementId: "G-EQ3EMB4GB8"
};

// Initialize Firebase AND Export the app instance
export const firebaseApp = initializeApp(firebaseConfig); // <--- Changed 'const app' to 'export const firebaseApp'

// Get and Export the Analytics instance (optional)
export const analytics = getAnalytics(firebaseApp); // <--- Changed 'const analytics' to 'export const analytics' and use firebaseApp

// Get and Export the Auth instance (needed for SignIn.jsx)
export const auth = getAuth(firebaseApp); // <--- Add this line to get and export auth