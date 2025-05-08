import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../../services/authService';
import { Header } from '../Home/HomePage';

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
      <Header />

      <main className="flex-grow flex items-center justify-center py-12">
        <div className="container mx-auto px-4 sm:px-6 lg:px-8">
          <div className="max-w-4xl mx-auto grid md:grid-cols-2 rounded-lg shadow-xl overflow-hidden">

            <div className="bg-[#1A365D] p-8 md:p-10 text-white flex flex-col items-center justify-center text-center">
              <img
                src={user?.profileImageUrl || '/icon-512.png'}
                alt="Profile"
                className="w-32 h-32 rounded-full border-4 border-white shadow-lg object-cover mb-5"
                referrerPolicy="no-referrer"
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

              <div className="mt-8 text-right space-x-4">
                <Link
                  to="/profile/edit"
                  className="inline-flex items-center px-6 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-[#2D8A8A] hover:bg-[#236b6b] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#2D8A8A] transition-colors duration-200 transform hover:-translate-y-0.5"
                >
                  Edit Profile
                </Link>
                <Link
                  to="/subscribe"
                  className="inline-flex items-center px-6 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-blue-600 hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors duration-200 transform hover:-translate-y-0.5"
                >
                  Apply Subscription
                </Link>
              </div>
            </div>

          </div>
        </div>
      </main>

    </div>
  );
};

export default UserProfile;