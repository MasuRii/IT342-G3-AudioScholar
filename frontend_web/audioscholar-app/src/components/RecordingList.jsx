// components/RecordingList.jsx
import React, { useState, useEffect } from 'react'; // Import useState and useEffect
import { Link, useNavigate } from 'react-router-dom';
import axios from 'axios'; // Import axios
// Import the base URL from the auth service
import { API_BASE_URL } from '../services/authService'; // <-- Corrected path

const RecordingList = () => {
  const navigate = useNavigate();

  // State to hold the actual recordings fetched from the backend
  const [recordings, setRecordings] = useState([]);
  // State to manage loading state
  const [loading, setLoading] = useState(true);
  // State to manage potential errors
  const [error, setError] = useState(null);

  // Function to fetch recordings from the backend
  const fetchRecordings = async () => {
    setLoading(true); // Set loading to true before fetching
    setError(null); // Clear any previous errors

    try {
      // *** IMPORTANT: Get your authentication token and include it in the header ***
      // This is an example using localStorage. You should replace this
      // with your actual method for getting the authentication token.
      const token = localStorage.getItem('AuthToken'); // <-- Corrected key

      if (!token) {
          // Handle case where user is not authenticated (token missing)
          setError("User not authenticated. Please log in.");
          setLoading(false);
          navigate('/signin'); // <-- Corrected redirect path to /signin
          return;
      }

      // Construct the full URL
      const metadataUrl = `${API_BASE_URL}api/audio/metadata`; // <-- Use API_BASE_URL and ensure no leading '/'

      const response = await axios.get(metadataUrl, { // <-- Use the full URL
        headers: {
          // Example: Using Bearer token authentication
          'Authorization': `Bearer ${token}`
        },
        // You can add params for pagination here if you implement it in the frontend
        // params: { pageSize: 10, lastId: lastFetchedId }
      });

      // Assuming the backend returns an array of AudioMetadata objects
      setRecordings(response.data);
      console.log("Fetched recordings:", response.data); // Log data for debugging

    } catch (err) {
      console.error('Error fetching recordings:', err);
      if (err.response) {
          // Handle specific HTTP errors from the backend
          if (err.response.status === 401 || err.response.status === 403) {
              setError("Session expired or not authorized. Please log in again.");
              // Clear invalid token if stored
              localStorage.removeItem('AuthToken'); // <-- Corrected key
              navigate('/signin'); // <-- Corrected redirect path to /signin
          } else {
             setError("Failed to fetch recordings. Status: " + err.response.status);
          }
      } else if (err.request) {
          // The request was made but no response was received
           setError("Network error: Could not reach the server.");
      } else {
          // Something happened in setting up the request that triggered an Error
           setError("An unexpected error occurred.");
      }
    } finally {
      setLoading(false); // Set loading to false after fetching (success or failure)
    }
  };

  // Fetch recordings when the component mounts
  useEffect(() => {
    fetchRecordings();
  }, []); // The empty dependency array ensures this runs only once on mount

  // Add handleLogout function
  const handleLogout = () => {
    // Add any logout logic here (e.g., clearing authentication token)
    // localStorage.removeItem('authToken'); // Example: Clear token from local storage - Removed duplicate/incorrect key
    localStorage.removeItem('AuthToken'); // <-- Corrected key
    navigate('/'); // Redirect to landing page
  };

  // Handle delete functionality
  const handleDelete = async (id) => {
    // Optional: Add a confirmation dialog
    if (window.confirm('Are you sure you want to delete this recording?')) {
      try {
        // *** IMPORTANT: Get your authentication token and include it in the header ***
        const token = localStorage.getItem('AuthToken'); // <-- Corrected key

         if (!token) {
             alert("User not authenticated. Cannot delete.");
             navigate('/signin'); // <-- Corrected redirect path to /signin
             return;
         }

        // Construct the full URL for deletion
        const deleteUrl = `${API_BASE_URL}api/audio/metadata/${id}`; // <-- Use API_BASE_URL

        // Send DELETE request to the backend endpoint
        await axios.delete(deleteUrl, { // <-- Use the full URL
             headers: {
                 'Authorization': `Bearer ${token}` // Include auth token
             }
         });

        // If the request is successful (backend returns 204 No Content),
        // update the state to remove the deleted item from the list
        setRecordings(recordings.filter(recording => recording.id !== id));
        console.log(`Recording with ID ${id} deleted successfully.`); // Log success

      } catch (err) {
        console.error('Error deleting recording:', err);
         if (err.response) {
             if (err.response.status === 401 || err.response.status === 403) {
                 alert("Unauthorized to delete this recording.");
                  localStorage.removeItem('AuthToken'); // <-- Corrected key
                 navigate('/signin'); // <-- Corrected redirect path to /signin
             } else if (err.response.status === 404) {
                 alert("Recording not found.");
                 // Optionally refetch the list or remove it from state anyway
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
      {/* Header updated to include Profile button */}
      <header className="bg-[#1A365D] shadow-sm py-4">
        <div className="container mx-auto px-4 flex justify-between items-center">
          <Link to="/" className="text-2xl font-bold text-white">AudioScholar</Link>
          {/* Updated div for header buttons */}
          <div className="flex items-center space-x-2"> {/* Use items-center and space-x-2 */}
            <Link
              to="/dashboard"
                 // Added icon and adjusted classes
              className="flex items-center text-gray-300 hover:text-indigo-400 transition-colors py-2 px-3 rounded hover:bg-white hover:bg-opacity-10"
            >
               <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-1 sm:mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                 <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
               </svg>
               <span className="hidden sm:inline">Dashboard</span> {/* Adjusted text */}
               <span className="sm:hidden"></span>
            </Link>
            {/* Added Profile Link */}
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
            {/* Changed Logout Link to Button */}
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

      {/* Main Content */}
      <main className="flex-grow bg-gray-50">
        <div className="container mx-auto px-4 py-12">
          <div className="max-w-6xl mx-auto">
            <div className="flex justify-between items-center mb-8">
              <h1 className="text-3xl font-bold text-gray-800">Recording List</h1>
              <Link
                to="/upload"
                className="bg-[#2D8A8A] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#236b6b] transition flex items-center" // Added flex items-center
              >
                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 mr-2" fill="none" viewBox="0 0 24 24" stroke="currentColor"> {/* Added Icon */}
                   <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M12 12v6m0 0l-3-3m3 3l3-3" />
                 </svg>
                 New Upload {/* Removed + sign */}
              </Link>
            </div>

            {/* Display loading, error, or data */}
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
                    <thead className="bg-gray-50">
                      <tr>
                        <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Title</th>
                        <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                        <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Duration</th>
                        <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                         {/* Added View Data header */}
                        <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">View Data</th>
                        <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                      </tr>
                    </thead>
                    <tbody className="bg-white divide-y divide-gray-200">
                      {/* Map over the fetched recordings */}
                      {recordings.map((recording) => (
                        // Use recording.id as the key
                        <tr key={recording.id} className="hover:bg-gray-50">
                          <td className="px-6 py-4 whitespace-nowrap">
                            {/* Display title as simple text */}
                            <div className="text-sm font-medium text-gray-900">
                                {recording.title || 'Untitled Recording'}
                            </div>
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                            {/* Display date from backend data (assuming it's in a format Date can parse) */}
                            {/* You might need to format the date depending on your backend's output */}
                             <div className="text-sm text-gray-500">{new Date(recording.uploadDate).toLocaleDateString()}</div> {/* Assuming backend provides 'uploadDate' */}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                            {/* Display duration from backend data */}
                            <div className="text-sm text-gray-500">{recording.duration || 'N/A'}</div> {/* Assuming backend provides 'duration' */}
                          </td>
                          <td className="px-6 py-4 whitespace-nowrap">
                             {/* Display status from backend data and apply styles */}
                            <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                              recording.status === 'Processed'
                                 ? 'bg-green-100 text-green-800'
                                 : recording.status === 'Processing'
                                   ? 'bg-yellow-100 text-yellow-800'
                                   : 'bg-gray-100 text-gray-800' // Default color for other statuses
                            }`}>
                              {recording.status || 'Unknown'}
                            </span>
                          </td>
                           {/* Add new Cell for View Data Link */}
                          <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                              {recording.status === 'Processed' ? (
                                 <Link to={`/recordings/${recording.id}`} className="text-[#2D8A8A] hover:text-[#236b6b]">
                                     View Data
                                 </Link>
                              ) : (
                                 <span className="text-gray-400 cursor-not-allowed">
                                     View Data
                                 </span>
                              )}
                           </td>
                          <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                            {/* Delete button - add onClick handler */}
                            <button
                              onClick={() => handleDelete(recording.id)} // Call handleDelete with the recording's ID
                              className="text-red-600 hover:text-red-900" // Use red color for delete
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

      {/* Footer */}
      <footer className="bg-gray-100 py-12">
        {/* Same footer as in other pages */}
        <div className="container mx-auto text-center text-gray-600">
            &copy; {new Date().getFullYear()} AudioScholar. All rights reserved.
        </div>
      </footer>
    </div>
  );
};

export default RecordingList;