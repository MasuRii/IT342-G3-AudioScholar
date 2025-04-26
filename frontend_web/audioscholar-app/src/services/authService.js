// src/services/authService.js

// Base URL of your backend API (ensure this is correct and accessible)
// You had two lines, I will keep the first active one and add the export
export const API_BASE_URL = 'https://it342-g3-audioscholar-onrender-com.onrender.com/';
//  // <--- Added 'export'!

//const API_BASE_URL = 'https://mastodon-balanced-randomly.ngrok-free.app/'; // This line remains commented out

// --- Function to Verify Firebase ID Token with Backend ---
/**
 * Sends the Firebase ID token obtained from frontend Firebase authentication
 * to the backend for verification and to receive an API JWT.
 * @param {string} idToken - The Firebase ID token.
 * @returns {Promise<object>} - A promise that resolves with the backend's response (e.g., { success, message, token, userId })
 */
export const verifyFirebaseTokenWithBackend = async (idToken) => {
    // The specific path for your backend Firebase token verification endpoint
    // Based on your Java code: @PostMapping("/verify-firebase-token")
    // Assuming it's directly under the base URL. Adjust if it's nested (e.g., 'api/auth/verify-firebase-token')
    const VERIFY_ENDPOINT_PATH = 'api/auth/verify-firebase-token'; // Relative path - looks correct with '/api/auth' prefix now

    try {
        // Using template literal to combine base URL and path
        // Since API_BASE_URL ends with '/', VERIFY_ENDPOINT_PATH should not start with '/'
        const response = await fetch(`${API_BASE_URL}${VERIFY_ENDPOINT_PATH}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                // Include any other headers your backend requires
            },
            // Send the idToken in the format expected by your backend's FirebaseTokenRequest DTO
            body: JSON.stringify({ idToken: idToken }),
        });

        // Always try to parse the response body, even for errors, as it might contain messages
        const responseData = await response.json();

        // Check if the request was successful (status code 2xx)
        if (!response.ok) {
            // Throw an error using the message from the backend's response if available
            const error = new Error(responseData.message || `Backend verification failed: ${response.statusText}`);
            error.status = response.status;
            error.data = responseData; // Attach full response data to the error object
            throw error;
        }

        // Optional: Check if the backend response indicates success explicitly (if applicable)
        // This matches the AuthResponse structure shown in your Java code
         if (!responseData.success || !responseData.token) {
             const error = new Error(responseData.message || 'Backend verification succeeded but response format is incorrect or token missing.');
             error.data = responseData;
             throw error;
         }

        // Return the successful response data (contains API token, userId, etc.)
        return responseData;

    } catch (error) {
        console.error("Error during backend Firebase token verification API call:", error);
        // Re-throw the error so the calling component (SignIn.jsx) can handle it
        throw error;
    }
};
// --- End of Firebase token verification function ---


// --- Function for user sign up ---
// This function remains unchanged, assuming it's used for a different purpose
// or perhaps a step after Firebase auth in some cases.
export const signUp = async (userData) => {
    // Replace '/api/auth/register' if your actual sign-up endpoint is different
    const SIGNUP_ENDPOINT_PATH = 'api/auth/register'; // Path relative to base URL

    try {
        const response = await fetch(`${API_BASE_URL}${SIGNUP_ENDPOINT_PATH}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(userData),
        });

        const errorData = await response.json(); // Parse JSON for both success and error

        if (!response.ok) {
            const error = new Error(errorData.message || `HTTP error! status: ${response.status}`);
            error.status = response.status;
            error.data = errorData;
            throw error;
        }

        return errorData; // Return the data received from the backend on success

    } catch (error) {
        console.error("Error during sign up API call:", error);
        throw error;
    }
};
// --- End of sign up function ---

// Note: The original 'signIn' function is now replaced by 'verifyFirebaseTokenWithBackend'.
// If you need other auth-related API calls (e.g., sign out involving the backend,
// password reset not handled by Firebase, etc.), add them here.