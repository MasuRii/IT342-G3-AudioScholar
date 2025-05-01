import { getAnalytics } from "firebase/analytics";
import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";


const firebaseConfig = {
  apiKey: "AIzaSyC-FRGSjiwzANRuSYxIY7Zm_1coY8viajc",
  authDomain: "audioscholar-39b22.firebaseapp.com",
  databaseURL: "https://audioscholar-39b22-default-rtdb.firebaseio.com",
  projectId: "audioscholar-39b22",
  messagingSenderId: "94663253748",
  appId: "1:94663253748:web:049c1293085af7caff48a1",
  measurementId: "G-EQ3EMB4GB8"
};

export const firebaseApp = initializeApp(firebaseConfig);

export const analytics = getAnalytics(firebaseApp);

export const auth = getAuth(firebaseApp);