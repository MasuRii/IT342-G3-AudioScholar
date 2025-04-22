// components/RecordingList.jsx
import { Link } from 'react-router-dom';

const RecordingList = () => {
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

  return (
    <div className="min-h-screen flex flex-col">
      {/* Header with Logout Button */}
      <header className="bg-[#1A365D] shadow-sm py-4">
        <div className="container mx-auto px-4 flex justify-between items-center">
          <Link to="/" className="text-2xl font-bold text-white">AudioScholar</Link>
          <div className="flex space-x-4">
            <Link 
              to="/dashboard" 
              className="text-gray-300 hover:text-indigo-400 transition-colors py-2 px-4 rounded hover:bg-white hover:bg-opacity-10"
            >
             back to  Dashboard
            </Link>
            <Link 
              to="/"
              className="text-gray-300 hover:text-indigo-400 transition-colors py-2 px-4 rounded hover:bg-white hover:bg-opacity-10"
            >
              Logout
            </Link>
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
                className="bg-[#2D8A8A] text-white py-2 px-4 rounded-lg font-medium hover:bg-[#236b6b] transition"
              >
                + New Upload
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
                          <button className="text-gray-600 hover:text-gray-900">Delete</button>
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
      </footer>
    </div>
  );
};

export default RecordingList;