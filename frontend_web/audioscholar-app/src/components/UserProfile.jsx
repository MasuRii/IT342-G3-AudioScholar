// components/UserProfile.jsx
import { Link } from 'react-router-dom';

const UserProfile = () => {
  // Mock user data - replace with actual data from your API or state management
  const user = {
    name: "John Doe",
    email: "john.doe@example.com",
    age: 32,
    gender: "Male",
    address: "123 Main St, Anytown, USA",
    joinDate: "January 15, 2023",
    subscription: "Premium",
    profileImage: "https://randomuser.me/api/portraits/men/1.jpg"
  };

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
              Back to Dashboard
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
      <main className="flex-grow bg-gray-50 py-12">
        <div className="container mx-auto px-4">
          <div className="max-w-4xl mx-auto">
            <div className="bg-white rounded-lg shadow-md overflow-hidden">
              {/* Profile Header */}
              <div className="bg-[#2D8A8A] p-6 text-white">
                <div className="flex flex-col md:flex-row items-center">
                  <div className="mb-4 md:mb-0 md:mr-6">
                    <img 
                      src={user.profileImage} 
                      alt="Profile" 
                      className="w-24 h-24 rounded-full border-4 border-white border-opacity-50 object-cover"
                    />
                  </div>
                  <div>
                    <h1 className="text-2xl font-bold">{user.name}</h1>
                    <p className="text-[#d1f0f0]">{user.email}</p>
                    <div className="mt-2 flex flex-wrap gap-2">
                      <span className="bg-white bg-opacity-20 px-3 py-1 rounded-full text-sm">
                        {user.subscription} Member
                      </span>
                      <span className="bg-white bg-opacity-20 px-3 py-1 rounded-full text-sm">
                        Joined {user.joinDate}
                      </span>
                    </div>
                  </div>
                </div>
              </div>

              {/* Profile Details */}
              <div className="p-6">
                <h2 className="text-xl font-semibold text-gray-800 mb-4">Personal Information</h2>
                
                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                  <div className="bg-gray-50 p-4 rounded-lg">
                    <h3 className="text-sm font-medium text-gray-500 mb-1">Full Name</h3>
                    <p className="text-gray-800 font-medium">{user.name}</p>
                  </div>
                  
                  <div className="bg-gray-50 p-4 rounded-lg">
                    <h3 className="text-sm font-medium text-gray-500 mb-1">Age</h3>
                    <p className="text-gray-800 font-medium">{user.age}</p>
                  </div>
                  
                  <div className="bg-gray-50 p-4 rounded-lg">
                    <h3 className="text-sm font-medium text-gray-500 mb-1">Gender</h3>
                    <p className="text-gray-800 font-medium">{user.gender}</p>
                  </div>
                  
                  <div className="bg-gray-50 p-4 rounded-lg md:col-span-2">
                    <h3 className="text-sm font-medium text-gray-500 mb-1">Address</h3>
                    <p className="text-gray-800 font-medium">{user.address}</p>
                  </div>
                </div>

                {/* Edit Button */}
                <div className="mt-8">
                  <Link 
                    to="/profile/edit" 
                    className="inline-flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-md shadow-sm text-white bg-[#2D8A8A] hover:bg-[#236b6b] focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-[#2D8A8A]"
                  >
                    Edit Profile
                  </Link>
                </div>
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

export default UserProfile;