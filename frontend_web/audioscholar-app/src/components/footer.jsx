const Footer = () => {
    return (
      <footer className="bg-gray-900 text-white pt-16 pb-8">
        <div className="container mx-auto px-4">
          <div className="grid md:grid-cols-4 gap-8">
            <div>
              <h3 className="text-xl font-bold mb-4 text-primary-400">AudioScholar</h3>
              <p className="text-gray-400 mb-4">Transform your audio experience with AI-powered solutions.</p>
              <div className="flex space-x-4">
                <a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">
                  {/* Twitter icon */}
                </a>
                <a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">
                  {/* Facebook icon */}
                </a>
                <a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">
                  {/* Instagram icon */}
                </a>
              </div>
            </div>
            
            <div>
              <h3 className="text-lg font-semibold text-white mb-4">Product</h3>
              <ul className="space-y-2">
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Features</a></li>
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Pricing</a></li>
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Documentation</a></li>
              </ul>
            </div>
            
            <div>
              <h3 className="text-lg font-semibold text-white mb-4">Company</h3>
              <ul className="space-y-2">
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">About</a></li>
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Blog</a></li>
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Careers</a></li>
              </ul>
            </div>
            
            <div>
              <h3 className="text-lg font-semibold text-white mb-4">Legal</h3>
              <ul className="space-y-2">
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Privacy</a></li>
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Terms</a></li>
                <li><a href="#" className="text-gray-400 hover:text-primary-400 transition-colors">Contact</a></li>
              </ul>
            </div>
          </div>
          
          <div className="border-t border-gray-800 mt-12 pt-8 text-center text-gray-400">
            <p>&copy; {new Date().getFullYear()} AudioScholar. All rights reserved.</p>
          </div>
        </div>
      </footer>
    );
  };

  export default Footer;