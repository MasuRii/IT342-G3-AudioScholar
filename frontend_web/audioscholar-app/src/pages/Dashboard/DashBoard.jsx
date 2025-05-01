import { Link, useNavigate } from 'react-router-dom';
import { Header } from '../Home/HomePage';

const Dashboard = () => {
  const navigate = useNavigate();

  const handleLogout = () => {
    localStorage.removeItem('AuthToken');
    localStorage.removeItem('userId');
    navigate('/');
  };

  return (
    <div className="min-h-screen flex flex-col">
      <title>AudioScholar - Dashboard</title>
      <Header />

      <main className="flex-grow bg-gray-50">
        <div className="container mx-auto px-4 py-12">
          <div className="max-w-6xl mx-auto">
            <h1 className="text-3xl font-bold text-gray-800 mb-10">Dashboard</h1>

            <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
              <Link
                to="/upload"
                className="bg-white rounded-lg shadow-md p-6 hover:shadow-xl transition-all duration-300 ease-in-out transform hover:-translate-y-1 cursor-pointer border border-gray-200"
              >
                <div className="flex items-center mb-4">
                  <div className="bg-[#2D8A8A] bg-opacity-20 p-3 rounded-full mr-4">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6 text-[#2D8A8A]" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M15 13l-3-3m0 0l-3 3m3-3v12" />
                    </svg>
                  </div>
                  <h2 className="text-xl font-semibold text-gray-800">Upload Audio</h2>
                </div>
                <p className="text-gray-600">Upload new audio files to generate summaries and notes.</p>
              </Link>

              <Link
                to="/recordings"
                className="bg-white rounded-lg shadow-md p-6 hover:shadow-xl transition-all duration-300 ease-in-out transform hover:-translate-y-1 cursor-pointer border border-gray-200"
              >
                <div className="flex items-center mb-4">
                  <div className="bg-blue-100 p-3 rounded-full mr-4">
                    <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6 text-blue-600" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 19V6l12-3v13M9 19c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zm12-3c0 1.105-1.343 2-3 2s-3-.895-3-2 1.343-2 3-2 3 .895 3 2zM9 10l12-3" />
                    </svg>
                  </div>
                  <h2 className="text-xl font-semibold text-gray-800">Recording List</h2>
                </div>
                <p className="text-gray-600">View and manage all your audio recordings.</p>
              </Link>

            </div>
          </div>
        </div>
      </main>

      <footer className="bg-gray-100 py-12">
      </footer>
    </div>
  );
};

export default Dashboard;