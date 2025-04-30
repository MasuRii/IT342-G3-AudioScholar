import axios from 'axios';
import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { API_BASE_URL } from '../services/authService';

const RecordingList = () => {
  const navigate = useNavigate();

  const [recordings, setRecordings] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchRecordings = async () => {
    setLoading(true);
    setError(null);

    try {
      const token = localStorage.getItem('AuthToken');

      if (!token) {
        setError("User not authenticated. Please log in.");
        setLoading(false);
        navigate('/signin');
        return;
      }

      const metadataUrl = `${API_BASE_URL}api/audio/metadata`;

      const response = await axios.get(metadataUrl, {
        headers: {
          'Authorization': `Bearer ${token}`
        },
      });

      setRecordings(response.data);
      console.log("Fetched recordings:", response.data);

    } catch (err) {
      console.error('Error fetching recordings:', err);
      if (err.response) {
        if (err.response.status === 401 || err.response.status === 403) {
          setError("Session expired or not authorized. Please log in again.");
          localStorage.removeItem('AuthToken');
          navigate('/signin');
        } else {
          setError("Failed to fetch recordings. Status: " + err.response.status);
        }
      } else if (err.request) {
        setError("Network error: Could not reach the server.");
      } else {
        setError("An unexpected error occurred.");
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchRecordings();
  }, []);

  const handleLogout = () => {
    localStorage.removeItem('AuthToken');
    navigate('/');
  };

  const handleDelete = async (id) => {
    if (window.confirm('Are you sure you want to delete this recording?')) {
      try {
        const token = localStorage.getItem('AuthToken');

        if (!token) {
          alert("User not authenticated. Cannot delete.");
          navigate('/signin');
          return;
        }

        const deleteUrl = `${API_BASE_URL}api/audio/metadata/${id}`;

        await axios.delete(deleteUrl, {
          headers: {
            'Authorization': `Bearer ${token}`
          }
        });

        setRecordings(recordings.filter(recording => recording.id !== id));
        console.log(`Recording with ID ${id} deleted successfully.`);

      } catch (err) {
        console.error('Error deleting recording:', err);
        if (err.response) {
          if (err.response.status === 401 || err.response.status === 403) {
            alert("Unauthorized to delete this recording.");
            localStorage.removeItem('AuthToken');
            navigate('/signin');
          } else if (err.response.status === 404) {
            alert("Recording not found.");
            setRecordings(recordings.filter(recording => recording.id !== id));
          } else {
            alert(`Failed to delete recording. Status: ${err.response.status}`);
          }
        } else {
          alert("Network error or unexpected issue during deletion.");
        }
      }
    }
  };


  return (
    <div className="min-h-screen flex flex-col">
      <title>AudioScholar - Recordings</title>
      <header className="bg-[#1A365D] shadow-sm py-4">
        <div className="container mx-auto px-4 flex justify-between items-center">
          <Link to="/dashboard" className="text-2xl font-bold text-white">AudioScholar</Link>
          <div className="flex items-center space-x-2">
            <Link
              to="/dashboard"
              className="flex items-center text-gray-300 hover:text-indigo-400 transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
              </svg>
              <span className="hidden sm:inline">Dashboard</span>
              <span className="sm:hidden"></span>
            </Link>
            <Link
              to="/profile"
              className="flex items-center text-gray-300 hover:text-indigo-400 transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7z" />
              </svg>
              <span className="hidden sm:inline">Profile</span>
              <span className="sm:hidden"></span>
            </Link>
            <button
              onClick={handleLogout}
              className="flex items-center text-gray-300 hover:text-indigo-400 transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
              <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
              </svg>
              <span className="hidden sm:inline">Logout</span>
              <span className="sm:hidden"></span>
            </button>
          </div>
        </div>
      </header>

      <main className="flex-grow bg-gray-50">
        <div className="container mx-auto px-4 py-12">
          <div className="max-w-6xl mx-auto">
            <div className="flex justify-between items-center mb-8">
              <h1 className="text-3xl font-bold text-gray-800">Recording List</h1>
              <Link
                to="/upload"
                className="bg-[#2D8A8A] text-white py-2 px-5 rounded-lg font-medium hover:bg-[#236b6b] transition-colors duration-200 flex items-center shadow hover:shadow-md transform hover:-translate-y-0.5"
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M12 12v6m0 0l-3-3m3 3l3-3" />
                </svg>
                New Upload
              </Link>
            </div>

            {loading && (
              <div className="text-center text-gray-600">Loading recordings...</div>
            )}

            {error && (
              <div className="text-center text-red-500 mb-4">{error}</div>
            )}

            {!loading && !error && recordings.length === 0 && (
              <div className="text-center text-gray-600">No recordings found. Upload one to get started!</div>
            )}

            {!loading && !error && recordings.length > 0 && (
              <div className="bg-white rounded-lg shadow-md overflow-hidden">
                <div className="overflow-x-auto">
                  <table className="min-w-full divide-y divide-gray-200">
                    <thead className="bg-gray-100">
                      <tr>
                        <th scope="col" className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Title</th>
                        <th scope="col" className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Date</th>
                        <th scope="col" className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Duration</th>
                        <th scope="col" className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Status</th>
                        <th scope="col" className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">View Data</th>
                        <th scope="col" className="px-6 py-4 text-left text-xs font-semibold text-gray-600 uppercase tracking-wider">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {recordings.map((recording) => (
                        <tr key={recording.id} className="hover:bg-gray-50 transition-colors duration-150">
                          <td className="px-6 py-5 whitespace-nowrap">
                            <div className="text-sm font-medium text-gray-900">
                              {recording.title || 'Untitled Recording'}
                            </div>
                          </td>
                          <td className="px-6 py-5 whitespace-nowrap">
                            <div className="text-sm text-gray-500">{new Date(recording.uploadDate).toLocaleDateString()}</div>
                          </td>
                          <td className="px-6 py-5 whitespace-nowrap">
                            <div className="text-sm text-gray-500">{recording.duration || 'N/A'}</div>
                          </td>
                          <td className="px-6 py-5 whitespace-nowrap">
                            <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${recording.status?.toLowerCase() === 'processed' || recording.status?.toLowerCase() === 'completed'
                              ? 'bg-green-100 text-green-800'
                              : recording.status?.toLowerCase() === 'processing'
                                ? 'bg-yellow-100 text-yellow-800'
                                : 'bg-gray-100 text-gray-800' // Default color for other statuses (e.g., FAILED, UNKNOWN)
                              }`}>
                              {recording.status || 'Unknown'}
                            </span>
                          </td>
                          <td className="px-6 py-5 whitespace-nowrap text-sm font-medium">
                            {(recording.status?.toLowerCase() === 'processed' || recording.status?.toLowerCase() === 'completed') ? (
                              <Link to={`/recordings/${recording.id}`} className="text-[#2D8A8A] hover:text-[#236b6b] transition-colors duration-150">
                                View Data
                              </Link>
                            ) : (
                              <span className="text-gray-400 cursor-not-allowed">
                                View Data
                              </span>
                            )}
                          </td>
                          <td className="px-6 py-5 whitespace-nowrap text-sm font-medium">
                            <button
                              onClick={() => handleDelete(recording.id)}
                              className="text-red-600 hover:text-red-800 transition-colors duration-150"
                            >
                              Delete
                            </button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            )}

          </div>
        </div>
      </main>

      <footer className="bg-gray-100 py-12">
        <div className="container mx-auto text-center text-gray-600">
          &copy; {new Date().getFullYear()} AudioScholar. All rights reserved.
        </div>
      </footer>
    </div>
  );
};

export default RecordingList;