// src/services/authService.js

// Replace with the base URL of your backend API
// For example: 'http://localhost:8080' if running locally
// Or the deployed URL of your backend
const API_BASE_URL = 'http://localhost:8080';

export const signUp = async (userData) => {
    // Replace '/auth/register' with the specific path for your sign-up endpoint
    // E.g., '/api/users', '/register', etc.
    const response = await fetch(`${API_BASE_URL}/http://localhost:8080/api/auth/register`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            // Include any other headers your backend requires (e.g., origin, API key)
        },
        body: JSON.stringify(userData),
    });

    // Check if the request was successful (status code 2xx)
    if (!response.ok) {
        // Attempt to parse error details from the response body
        // The backend should ideally return a JSON object with an error message
        const errorData = await response.json();
        // Throw an error including the status and the backend's message
        const error = new Error(errorData.message || `HTTP error! status: ${response.status}`);
        error.status = response.status; // Attach status for potential handling in component
        error.data = errorData; // Attach full error body
        throw error;
    }

    // Parse the successful response body (e.g., user data, auth token)
    const data = await response.json();
    return data; // Return the data received from the backend on success
};

// You can add other authentication related functions here later, e.g., signIn, signOut, etc.
// export const signIn = async (credentials) => { ... };