import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../../services/authService';

const GithubAuthCallback = () => {
  const [statusMessage, setStatusMessage] = useState('Processing GitHub login...');
  const [error, setError] = useState(null);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const queryParams = new URLSearchParams(location.search);
    const code = queryParams.get('code');
    const githubError = queryParams.get('error');
    const errorDescription = queryParams.get('error_description');

    if (githubError) {
      console.error(`GitHub OAuth Error: ${githubError} - ${errorDescription}`);
      setError(`GitHub login failed: ${errorDescription || githubError}`);
      setStatusMessage('Login failed.');
      return;
    }

    if (!code) {
      console.error('GitHub callback missing authorization code.');
      setError('GitHub login failed: Authorization code not found.');
      setStatusMessage('Login failed.');
      return;
    }

    const verifyCode = async () => {
      setStatusMessage('Verifying authorization...');
      try {
        const verifyUrl = `${API_BASE_URL}api/auth/verify-github-code`;
        console.log(`Sending POST request to ${verifyUrl} with code...`);

        const response = await axios.post(verifyUrl, { code });

        console.log('Backend verification response:', response.data);

        if (response.data && response.data.success && response.data.token && response.data.userId) {
          localStorage.setItem('AuthToken', response.data.token);
          localStorage.setItem('userId', response.data.userId);

          setStatusMessage('Login successful! Redirecting...');
          navigate('/dashboard');
        } else {
          const backendErrorMsg = response.data?.message || 'Backend verification failed.';
          console.error('Backend verification failed:', backendErrorMsg);
          setError(`Login failed: ${backendErrorMsg}`);
          setStatusMessage('Login failed.');
        }

      } catch (err) {
        console.error('Error sending code to backend:', err);
        let errMsg = 'An error occurred during verification.';
        if (err.response && err.response.data && err.response.data.message) {
          errMsg = err.response.data.message;
        } else if (err.message) {
          errMsg = err.message;
        }
        setError(`Login failed: ${errMsg}`);
        setStatusMessage('Login failed.');
      }
    };

    verifyCode();

  }, [location, navigate]);

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gray-100 p-4">
      <title>AudioScholar - Redirecting...</title>
      <div className="bg-white p-8 rounded-lg shadow-md text-center max-w-md w-full">
        <h1 className="text-2xl font-semibold mb-4 text-gray-800">GitHub Sign-In</h1>
        <p className="mb-6 text-gray-600">{statusMessage}</p>
        {error && (
          <div className="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded relative mb-4" role="alert">
            <strong className="font-bold">Error:</strong>
            <span className="block sm:inline"> {error}</span>
          </div>
        )}

        <p className="text-xs text-gray-400 mt-6">
          You will be redirected shortly if login is successful.
        </p>
      </div>
    </div>
  );
};

export default GithubAuthCallback; 