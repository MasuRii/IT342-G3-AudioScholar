import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../services/authService';

const UserProfile = () => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchUserData = async () => {
      setLoading(true);
      setError(null);
      const token = localStorage.getItem('AuthToken');

      if (!token) {
        setError('Not authenticated. Please log in.');
        setLoading(false);
        navigate('/signin');
        return;
      }

      try {
        const response = await axios.get(`${API_BASE_URL}api/users/me`, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });
        setUser(response.data);
        console.log("Fetched user profile:", response.data);
      } catch (err) {
        console.error('Error fetching user profile:', err);
        if (err.response && (err.response.status === 401 || err.response.status === 403)) {
          setError('Session expired or unauthorized. Please log in again.');
          localStorage.removeItem('AuthToken');
          localStorage.removeItem('userId');
          navigate('/signin');
        } else {
          setError('Failed to load user profile.');
        }
      } finally {
        setLoading(false);
      }
    };

    fetchUserData();
  }, [navigate]);

  const handleLogout = () => {
    localStorage.removeItem('AuthToken');
    localStorage.removeItem('userId');
    navigate('/');
  };

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-gray-100">
        <div className="text-center">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-teal-500 mx-auto mb-4"></div>
          Loading user profile...
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-gray-100 p-4">
        <div className="bg-white p-8 rounded-lg shadow-md text-center max-w-md w-full">
          <h1 className="text-2xl font-semibold mb-4 text-red-600">Error</h1>
          <p className="mb-6 text-gray-600">{error}</p>
          <Link to="/signin" className="text-teal-500 hover:underline">
            Go to Sign In
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-gray-100">
      <title>AudioScholar - Profile</title>
      <header className="bg-[#1A365D] shadow-sm py-4">
        <div className="container mx-auto px-4 sm:px-6 lg:px-8 flex justify-between items-center">
          <Link to="/dashboard" className="text-2xl font-bold text-white">AudioScholar</Link>
          <div className="flex items-center space-x-4">
            <Link
              to="/dashboard"
              className="flex items-center text-gray-300 hover:text-white transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
              </svg>
              <span className="hidden sm:inline">Dashboard</span>
            </Link>
            <button
              onClick={handleLogout}
              className="flex items-center text-gray-300 hover:text-white transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
              <span className="hidden sm:inline">Logout</span>
            </button>
          </div>
        </div>
      </header>

      <main className="flex-grow flex items-center justify-center py-12">
        <div className="container mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-4xl mx-auto grid md:grid-cols-2 rounded-lg shadow-xl overflow-hidden">

            <div className="bg-[#1A365D] p-8 md:p-10 text-white flex flex-col items-center justify-center text-center">
              <img
                src={user?.profileImageUrl || `https://ui-avatars.com/api/?name=${encodeURIComponent(user?.firstName || '')} ${encodeURIComponent(user?.lastName || '')}&background=2D8A8A&color=fff&size=128`}
                alt="Profile"
                className="w-32 h-32 rounded-full border-4 border-white shadow-lg object-cover mb-5"
              />
              <h1 className="text-3xl font-bold mb-1">{`${user?.firstName || ''} ${user?.lastName || ''}`.trim() || 'User Name'}</h1>
              <p className="text-indigo-200 mb-4 text-lg">{user?.email || 'email@example.com'}</p>
            </div>

            <div className="bg-white p-8 md:p-10">
              <h2 className="text-2xl font-semibold text-gray-800 mb-6">Personal Information</h2>

              <div className="space-y-5">
                <div>
                  <h3 className="text-sm font-medium text-gray-500 mb-1">First Name</h3>
                  <p className="text-gray-800 font-medium text-lg bg-gray-50 p-3 rounded-md border border-gray-200">{user?.firstName || 'N/A'}</p>
                </div>

                <div>
                  <h3 className="text-sm font-medium text-gray-500 mb-1">Last Name</h3>
                  <p className="text-gray-800 font-medium text-lg bg-gray-50 p-3 rounded-md border border-gray-200">{user?.lastName || 'N/A'}</p>
                </div>

                <div>
                  <h3 className="text-sm font-medium text-gray-500 mb-1">Email</h3>
                  <p className="text-gray-800 font-medium text-lg bg-gray-50 p-3 rounded-md border border-gray-200">{user?.email || 'N/A'}</p>
                </div>

              </div>

              <div className="mt-8 text-right">
                <Link
                  to="/profile/edit"
                  className="inline-flex items-center px-6 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-[#2D8A8A] hover:bg-[#236b6b] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#2D8A8A] transition-colors duration-200 transform hover:-translate-y-0.5"
                >
                  Edit Profile
                </Link>
              </div>
            </div>

          </div>
        </div>
      </main>

      <footer className="bg-gray-100 py-6 mt-auto">
        <div className="container mx-auto text-center text-gray-500 text-xs">
          &copy; {new Date().getFullYear()} AudioScholar. All rights reserved.
        </div>
      </footer>
    </div>
  );
};

export default UserProfile;