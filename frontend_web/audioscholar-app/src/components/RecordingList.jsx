// components/RecordingList.jsx
import { Link, useNavigate } from 'react-router-dom'; // Import useNavigate

const RecordingList = () => {
  const navigate = useNavigate(); // Initialize navigate

  // Mock data - replace with actual data from your API
  const recordings = [
    {
      id: 1,
      title: 'Lecture on Quantum Mechanics',
      date: '2023-05-15',
      duration: '45:22',
      status: 'Processed'
    },
    {
      id: 2,
      title: 'Meeting with Team',
      date: '2023-05-10',
      duration: '32:15',
      status: 'Processed'
    },
    {
      id: 3,
      title: 'Interview Preparation',
      date: '2023-05-05',
      duration: '28:40',
      status: 'Processing'
    },
    {
      id: 4,
      title: 'History Lecture',
      date: '2023-04-28',
      duration: '52:10',
      status: 'Processed'
    },
  ];

  // Add handleLogout function (similar to Uploading.jsx)
  const handleLogout = () => {
    // Add any logout logic here (clearing tokens, etc.)
    navigate('/'); // Redirect to landing page
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

            <div className="bg-white rounded-lg shadow-md overflow-hidden">
              <div className="overflow-x-auto">
                <table className="min-w-full divide-y divide-gray-200">
                  <thead className="bg-gray-50">
                    <tr>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Title</th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Date</th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Duration</th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Status</th>
                      <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white divide-y divide-gray-200">
                    {recordings.map((recording) => (
                      <tr key={recording.id} className="hover:bg-gray-50">
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="text-sm font-medium text-gray-900">{recording.title}</div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="text-sm text-gray-500">{recording.date}</div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <div className="text-sm text-gray-500">{recording.duration}</div>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap">
                          <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                            recording.status === 'Processed'
                              ? 'bg-green-100 text-green-800'
                              : 'bg-yellow-100 text-yellow-800'
                          }`}>
                            {recording.status}
                          </span>
                        </td>
                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium">
                          <Link to={`/recordings/${recording.id}`} className="text-[#2D8A8A] hover:text-[#236b6b] mr-4">View</Link>
                          <button className="text-gray-600 hover:text-gray-900">Delete</button> {/* Consider adding delete functionality */}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
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