export const API_BASE_URL = 'https://it342-g3-audioscholar.onrender.com';

/**
 * Sends the Firebase ID token obtained from frontend Firebase authentication
 * to the backend for verification and to receive an API JWT.
 * @param {string} idToken - The Firebase ID token.
 * @returns {Promise<object>} - A promise that resolves with the backend's response (e.g., { success, message, token, userId })
 */
export const verifyFirebaseTokenWithBackend = async (idToken) => {
    const VERIFY_ENDPOINT_PATH = 'api/auth/verify-firebase-token';

    try {
        const response = await fetch(`${API_BASE_URL}${VERIFY_ENDPOINT_PATH}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ idToken: idToken }),
        });

        const responseData = await response.json();

        if (!response.ok) {
            const error = new Error(responseData.message || `Backend verification failed: ${response.statusText}`);
            error.status = response.status;
            error.data = responseData;
            throw error;
        }

        if (!responseData.success || !responseData.token) {
            const error = new Error(responseData.message || 'Backend verification succeeded but response format is incorrect or token missing.');
            error.data = responseData;
            throw error;
        }

        return responseData;

    } catch (error) {
        console.error("Error during backend Firebase token verification API call:", error);
        throw error;
    }
};

/**
 * Sends the Google ID token obtained from frontend Firebase authentication
 * (specifically via Google Sign-In) to the backend for verification
 * and to receive an API JWT.
 * @param {string} googleIdToken - The raw Google ID token.
 * @returns {Promise<object>} - A promise that resolves with the backend's response.
 */
export const verifyGoogleTokenWithBackend = async (googleIdToken) => {
    const VERIFY_ENDPOINT_PATH = 'api/auth/verify-google-token';

    try {
        const response = await fetch(`${API_BASE_URL}${VERIFY_ENDPOINT_PATH}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify({ idToken: googleIdToken }),
        });

        const responseData = await response.json();

        if (!response.ok) {
            const error = new Error(responseData.message || `Backend Google verification failed: ${response.statusText}`);
            error.status = response.status;
            error.data = responseData;
            throw error;
        }

        if (!responseData.success || !responseData.token) {
            const error = new Error(responseData.message || 'Backend Google verification succeeded but response format is incorrect or token missing.');
            error.data = responseData;
            throw error;
        }

        return responseData;

    } catch (error) {
        console.error("Error during backend Google token verification API call:", error);
        throw error;
    }
};

export const signUp = async (userData) => {
    const SIGNUP_ENDPOINT_PATH = 'api/auth/register';

    try {
        const response = await fetch(`${API_BASE_URL}${SIGNUP_ENDPOINT_PATH}`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(userData),
        });

        const errorData = await response.json();

        if (!response.ok) {
            const error = new Error(errorData.message || `HTTP error! status: ${response.status}`);
            error.status = response.status;
            error.data = errorData;
            throw error;
        }

        return errorData;

    } catch (error) {
        console.error("Error during sign up API call:", error);
        throw error;
    }
};

