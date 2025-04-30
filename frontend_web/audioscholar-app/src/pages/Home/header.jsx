import { Link } from 'react-router-dom';

const Header = () => {
  return (
    <header className="bg-[#1A365D] shadow-sm py-4">
      <div className="container mx-auto px-4 flex justify-between items-center">
        <Link to="/" className="text-2xl font-bold text-white">AudioScholar</Link>
        <nav className="hidden md:flex space-x-8">
          <Link to="/signin" className="text-gray-300 hover:text-indigo-400 transition-colors">Sign in</Link>
          <Link to="/signup" className="text-gray-300 hover:text-indigo-400 transition-colors">Get Started</Link>
        </nav>
        <button className="md:hidden text-gray-300 hover:text-indigo-400">
          <svg xmlns="http://www.w3.org/2000/svg" className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
          </svg>
        </button>
      </div>
    </header>
  );
};

export default Header;